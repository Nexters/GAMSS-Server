# syntax=docker/dockerfile:1

# 1) 빌드 스테이지 — SPA를 정적 파일(dist)로 컴파일한다.
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 레이어 캐시: package 파일만 먼저 복사해 lockfile이 안 바뀌면 npm ci를 건너뛴다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# 2) 서빙 스테이지 — nginx로 정적 파일만 내보낸다(백엔드 없음).
# /api 프록시는 앞단 리버스 프록시가 맡고, 이 컨테이너는 순수 정적 서버다.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
