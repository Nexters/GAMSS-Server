#!/usr/bin/env bash
#
# GAMSS 애플리케이션 배포 (단순 재시작 방식)
#
# 사용법:
#   ./deploy-app.sh <배포_디렉토리> <이미지_태그>
#   예) ./deploy-app.sh ~/app dev-a1b2c3d
#
# 하는 일: .env의 IMAGE_TAG 갱신 → 이미지 pull → compose up -d → 헬스체크
#          헬스체크가 끝내 실패하면 이전 태그로 되돌리고 재기동한다.
#
# 앱 컨테이너만 교체되고 db는 실행 중이면 그대로 유지된다.
# 교체 중 20~40초의 다운타임이 있다(무중단 배포 아님).
#
set -euo pipefail

DEPLOY_DIR="${1:?사용법: $0 <배포_디렉토리> <이미지_태그>}"
NEW_TAG="${2:?사용법: $0 <배포_디렉토리> <이미지_태그>}"

readonly HEALTH_URL="http://127.0.0.1:8080/actuator/health"
readonly HEALTH_RETRIES=40
readonly HEALTH_INTERVAL=5

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

read_env_value() {
  local key="$1" file="$2" line
  [[ -f "$file" ]] || return 0
  line="$(grep -E "^${key}=" "$file" | tail -n1)" || return 0
  printf '%s' "${line#*=}"
}

write_env_value() {
  local key="$1" value="$2" file="$3"
  if grep -qE "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
    return
  fi
  echo "${key}=${value}" >> "$file"
}

# 모니터링 수집 포트(9100·9101·9102)를 바인딩할 사설 IP.
# 기본 라우트가 나가는 인터페이스의 주소를 쓴다 — `hostname -I` 는 docker0(172.17.x) 같은
# 브리지 주소가 먼저 나올 수 있어 신뢰할 수 없다.
#
# 결과는 RFC1918 대역(10/8·172.16~31/12·192.168/16)으로 한정한다. `scope global` 은 사설이
# 아니라 '링크로컬이 아님'을 뜻해서 공인 주소도 포함하는데, 그대로 쓰면 공인 인터페이스에
# 수집 포트를 열어버린다. 못 찾으면 빈 값을 반환하고, compose 가 127.0.0.1 로 떨어뜨려
# 수집만 안 되게 한다(0.0.0.0 으로 열리는 것보다 안전한 실패다).
detect_private_ip() {
  local iface addr
  iface="$(ip -4 route show default 2>/dev/null | awk '{print $5; exit}')" || return 0
  [[ -n "$iface" ]] || return 0
  addr="$(
    ip -4 -o addr show dev "$iface" scope global 2>/dev/null |
      awk '{print $4}' | cut -d/ -f1 |
      grep -E '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' | head -n1 || true
  )"
  printf '%s' "$addr"
}

wait_for_health() {
  local attempt
  for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
      log "헬스체크 통과 (${attempt}회 시도)"
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

restart_promtail() {
  # promtail.yml 은 bind mount 라, 파일만 바뀌면 `up -d` 가 컨테이너를 다시 만들지 않는다.
  # 그러면 새 설정이 반영되지 않은 채 이전 설정으로 계속 돈다(라벨이 바뀌어도 그대로).
  # 재시작 비용은 로그 수집 1~2초 공백뿐이고, 위치는 positions 볼륨에 남아 유실되지 않는다.
  if docker compose restart promtail >/dev/null 2>&1; then
    log "promtail 재시작 완료(설정 반영)"
    return
  fi
  log "promtail 재시작 실패 — 수집 설정이 이전 값일 수 있다"
}

reload_nginx() {
  # 컨테이너가 살아 있는지 먼저 본다. compose 파일이 바뀌면(볼륨·포트 추가 등) up -d 는 nginx 를
  # 재생성하는데, 그때 conf 가 잘못돼 기동에 실패하면 아래 nginx -t 도 함께 실패한다. 그 실패를
  # 문법 오류로 뭉뚱그리면 "이전 설정 유지"라는 사실이 아닌 로그가 남는다 - 유지될 이전 설정이
  # 없고 nginx 는 떠 있지도 않다.
  #
  # 그대로 두면 443 이 통째로 내려간 채 배포가 초록불로 끝난다. 헬스체크는 앱(127.0.0.1:8080)만
  # 보고, 롤백은 IMAGE_TAG 만 되돌려 conf 를 고치지도 못한다. 그래서 여기서 실패로 끝낸다.
  if [[ -z "$(docker compose ps --status running -q nginx)" ]]; then
    log "nginx 가 실행 중이 아니다 - 443(api·admin 포함)이 내려가 있다. 즉시 확인 필요"
    docker compose logs --tail=30 nginx || true
    exit 1
  fi

  # conf는 볼륨 마운트라 up -d 만으로는 갱신이 반영되지 않는다.
  # 문법 검사를 통과할 때만 무중단 reload 한다.
  if ! docker compose exec -T nginx nginx -t >/dev/null 2>&1; then
    log "nginx 설정 문법 오류 — reload 건너뛴다 (이전 설정 유지)"
    return
  fi

  if docker compose exec -T nginx nginx -s reload >/dev/null 2>&1; then
    log "nginx 설정 reload 완료"
    return
  fi
  log "nginx reload 실패 — 서버 수동 확인 필요"
}

rollback() {
  local prev_tag="$1"

  if [[ -z "$prev_tag" ]]; then
    log "이전 태그가 없어 롤백을 건너뛴다 (최초 배포로 판단)"
    return
  fi

  log "이전 태그로 롤백: ${prev_tag}"
  write_env_value IMAGE_TAG "$prev_tag" .env
  # set -e 하에서 up -d 가 실패해도 스크립트를 종료시키지 않고 아래 헬스체크·경고까지 진행한다.
  if ! docker compose up -d app; then
    log "롤백 컨테이너 기동 실패 — 서버 수동 확인 필요"
    return
  fi

  if wait_for_health; then
    log "롤백 후 헬스체크 통과 — 이전 버전으로 서비스 중"
    return
  fi
  log "롤백 후에도 헬스체크 실패 — 서버 수동 확인 필요"
}

require_files() {
  [[ -f .env ]] || { log ".env 없음: ${DEPLOY_DIR}/.env"; exit 1; }
  [[ -f docker-compose.yml ]] || { log "docker-compose.yml 없음: ${DEPLOY_DIR}"; exit 1; }
}

main() {
  cd "$DEPLOY_DIR"
  require_files

  local prev_tag
  prev_tag="$(read_env_value IMAGE_TAG .env)"
  log "이전 태그: ${prev_tag:-(없음)} → 새 태그: ${NEW_TAG}"

  write_env_value IMAGE_TAG "$NEW_TAG" .env

  # 매 배포마다 다시 감지한다 — 서버를 옮기거나 NIC 가 바뀌어도 .env 를 손대지 않아도 되게.
  local private_ip
  private_ip="$(detect_private_ip || true)"
  if [[ -n "$private_ip" ]]; then
    write_env_value PRIVATE_IP "$private_ip" .env
    log "사설 IP 감지: ${private_ip} (모니터링 수집 포트 바인딩)"
  else
    # 실패했을 때 이전 값을 남겨두면 그 주소에 계속 바인딩된다(서버 이전·NIC 교체 후 위험).
    # 빈 값으로 덮어써야 compose 의 기본값(127.0.0.1)이 실제로 적용된다.
    write_env_value PRIVATE_IP "" .env
    log "사설 IP 를 찾지 못했다 — 수집 포트는 127.0.0.1 에만 열린다(외부 노출 없음)"
  fi

  log "이미지 pull: ${NEW_TAG}"
  if ! docker compose pull app admin; then
    write_env_value IMAGE_TAG "${prev_tag}" .env
    log "이미지 pull 실패 — .env를 원복하고 중단한다"
    exit 1
  fi

  log "컨테이너 기동 (db·nginx는 실행 중이면 유지)"
  # set -e 하에서 up -d 가 실패(포트 충돌·자원 부족 등)하면 스크립트가 즉시 종료돼
  # 아래 헬스체크·자동 롤백이 실행되지 않으므로, pull 과 동일하게 감싸 롤백으로 넘긴다.
  if ! docker compose up -d; then
    log "컨테이너 기동 실패"
    docker compose logs --tail=50 app || true
    rollback "$prev_tag"
    exit 1
  fi

  log "헬스체크 대기 (최대 $((HEALTH_RETRIES * HEALTH_INTERVAL))초)"
  if wait_for_health; then
    reload_nginx
    restart_promtail
    log "배포 완료: ${NEW_TAG}"
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
  fi

  log "헬스체크 실패 — 앱 로그 마지막 50줄"
  docker compose logs --tail=50 app || true
  rollback "$prev_tag"
  exit 1
}

main "$@"
