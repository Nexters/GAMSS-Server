#!/usr/bin/env bash
#
# GAMSS 배포 서버 초기 세팅 스크립트 (Ubuntu 22.04 / 24.04)
#
# 사용법:
#   sudo bash setup-server.sh [배포에_사용할_사용자명]
#   예) sudo bash setup-server.sh ubuntu
#
# 하는 일: 패키지 업데이트 → Docker 설치 → 배포 유저를 docker 그룹에 추가
#          → 방화벽(22/80/443) → 배포 디렉토리 생성
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

echo "== 1/5 패키지 업데이트 =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y

echo "== 2/5 Docker 설치 =="
if command -v docker >/dev/null 2>&1; then
  echo "  이미 설치됨: $(docker --version)"
else
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "== 3/5 배포 유저(${DEPLOY_USER})를 docker 그룹에 추가 =="
usermod -aG docker "${DEPLOY_USER}" || true

echo "== 4/5 방화벽(ufw): 22/80/443만 허용 =="
if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw --force enable
  echo "  ufw 활성화 완료 (DB 포트 3306은 외부 미개방)"
else
  echo "  ufw 미설치 — 가비아 콘솔 보안규칙(22/80/443)으로 대체하세요."
fi

echo "== 5/5 배포 디렉토리 생성 (${DEPLOY_HOME}/app) =="
install -d -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_HOME}/app"

echo ""
echo "== 완료 =="
docker --version
docker compose version || true
echo "→ docker 그룹 반영을 위해 ${DEPLOY_USER} 재로그인(또는 'newgrp docker') 필요"
