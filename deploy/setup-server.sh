#!/usr/bin/env bash
#
# GAMSS 배포 서버 초기 세팅 스크립트 (Ubuntu 22.04 / 24.04)
#
# 사용법:
#   sudo bash setup-server.sh [배포에_사용할_사용자명]
#   예) sudo bash setup-server.sh ubuntu
#
# 하는 일: 패키지 업데이트 → Docker 설치 → 로그 로테이션 기본값 → 배포 유저를
#          docker 그룹에 추가 → 방화벽(22/80/443) → 배포 디렉토리 생성
# 멱등(여러 번 실행해도 안전)하게 작성됨.
#
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "root 권한이 필요합니다. 'sudo bash setup-server.sh' 로 실행하세요." >&2
  exit 1
fi

DEPLOY_USER="${1:-${SUDO_USER:-root}}"
DEPLOY_HOME="$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)"
DEPLOY_HOME="${DEPLOY_HOME:-/root}"

echo "== 1/6 패키지 업데이트 =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y

echo "== 2/6 Docker 설치 =="
if command -v docker >/dev/null 2>&1; then
  echo "  이미 설치됨: $(docker --version)"
else
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "== 3/6 도커 로그 로테이션 기본값(/etc/docker/daemon.json) =="
# compose에 명시된 서비스는 그쪽 설정을 따르고, 이 기본값은 그 외 임시 컨테이너까지 덮는다.
# live-restore: 데몬 재시작·업그레이드 때 실행 중인 컨테이너를 유지한다.
if [[ -f /etc/docker/daemon.json ]]; then
  echo "  이미 존재 — log-driver/log-opts가 설정돼 있는지 직접 확인하세요."
else
  cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "live-restore": true
}
EOF
  # 로그 기본값 적용에는 데몬 재시작이 필요한데, 운영 중인 서버에서 재실행된 경우라면
  # 전 컨테이너가 내려갔다 올라온다. 실행 중인 컨테이너가 있으면 재시작을 미루고 안내만 한다.
  if [[ -n "$(docker ps -q)" ]]; then
    echo "  실행 중인 컨테이너가 있어 도커 재시작을 건너뜁니다."
    echo "  → 트래픽 적은 시간에 'systemctl restart docker'를 직접 실행하세요."
  else
    systemctl restart docker
    echo "  작성 완료 (기존 컨테이너는 재생성해야 적용됨)"
  fi
fi

echo "== 4/6 배포 유저(${DEPLOY_USER})를 docker 그룹에 추가 =="
usermod -aG docker "${DEPLOY_USER}" || true

echo "== 5/6 방화벽(ufw): 22/80/443만 허용 =="
if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw --force enable
  echo "  ufw 활성화 완료 (DB 포트 3306은 외부 미개방)"
else
  echo "  ufw 미설치 — 가비아 콘솔 보안규칙(22/80/443)으로 대체하세요."
fi

echo "== 6/6 배포 디렉토리 생성 (${DEPLOY_HOME}/app) =="
install -d -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_HOME}/app"

echo ""
echo "== 완료 =="
docker --version
docker compose version || true
echo "→ docker 그룹 반영을 위해 ${DEPLOY_USER} 재로그인(또는 'newgrp docker') 필요"
