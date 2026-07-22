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

reload_nginx() {
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
