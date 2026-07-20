#!/usr/bin/env bash
# 백오피스 로컬 개발 한 번에 띄우기: MySQL + 백엔드(:8080) + 프론트(:5173).
# 백엔드는 백그라운드(로그 파일), 프론트는 포그라운드. Ctrl+C 하면 백엔드도 함께 종료된다.
# (MySQL 컨테이너는 데이터 유지를 위해 남겨둔다. 끄려면: docker compose down)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG="$SCRIPT_DIR/.backend.log"

backend_pid=""
cleanup() {
  echo
  echo "정리 중… 백엔드 종료"
  [ -n "$backend_pid" ] && kill "$backend_pid" 2>/dev/null || true
  # gradle 이 띄운 JVM 이 남을 수 있어 8080 점유 프로세스까지 정리한다.
  lsof -ti:8080 2>/dev/null | xargs kill 2>/dev/null || true
  echo "완료. (MySQL 은 유지 — 끄려면: docker compose down)"
}
trap cleanup EXIT INT TERM

echo "[1/3] MySQL 기동…"
(cd "$ROOT_DIR" && docker compose up -d >/dev/null)
for _ in $(seq 1 30); do
  docker exec gamss-mysql mysqladmin ping -h localhost --silent >/dev/null 2>&1 && break
  sleep 1
done
echo "      MySQL ready"

echo "[2/3] 백엔드 기동(:8080)… 로그: $LOG"
(cd "$ROOT_DIR" && ./gradlew bootRun) >"$LOG" 2>&1 &
backend_pid=$!
for _ in $(seq 1 60); do
  curl -sf -o /dev/null http://localhost:8080/actuator/health 2>/dev/null && break
  kill -0 "$backend_pid" 2>/dev/null || { echo "백엔드 기동 실패. 로그: $LOG"; exit 1; }
  sleep 1
done
echo "      백엔드 ready"

echo "[3/3] 프론트 dev 서버(:5173) — 종료하려면 Ctrl+C"
cd "$SCRIPT_DIR" && npm run dev
