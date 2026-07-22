# syntax=docker/dockerfile:1

# 1) 빌드 스테이지 — SPA를 정적 파일(dist)로 컴파일한다.
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 레이어 캐시: package 파일만 먼저 복사해 lockfile이 안 바뀌면 npm ci를 건너뛴다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite는 빌드 시점에 VITE_* 값을 번들에 인라인한다(공개값이라 안전).
# CD가 build-arg 로 넘긴 값을 ENV 로 승격해 npm run build 가 읽게 한다.
ARG VITE_API_BASE_URL=""
ARG VITE_FIREBASE_API_KEY=""
ARG VITE_FIREBASE_AUTH_DOMAIN=""
ARG VITE_FIREBASE_PROJECT_ID=""
ARG VITE_FIREBASE_APP_ID=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_FIREBASE_API_KEY=$VITE_FIREBASE_API_KEY \
    VITE_FIREBASE_AUTH_DOMAIN=$VITE_FIREBASE_AUTH_DOMAIN \
    VITE_FIREBASE_PROJECT_ID=$VITE_FIREBASE_PROJECT_ID \
    VITE_FIREBASE_APP_ID=$VITE_FIREBASE_APP_ID

RUN npm run build

# 2) 서빙 스테이지 — nginx로 정적 파일만 내보낸다(백엔드 없음).
# /api 프록시는 앞단 리버스 프록시가 맡고, 이 컨테이너는 순수 정적 서버다.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
