# admin-web 배포 메모

백오피스는 **정적 SPA**다. `admin-web/Dockerfile`이 SPA를 빌드해 nginx 이미지로 굽고,
서버의 앞단 리버스 프록시가 `admin.gamss.kr` 요청을 이 컨테이너로 넘긴다.
SPA는 API를 상대경로 `/api/...`로 부르므로, 앞단 nginx가 `/api`만 앱으로 프록시하면
**same-origin → CORS 불필요**. 이미지는 dev·prod 공용이다(각 서버가 자기 앱을 바라봄).

배포 인프라(PR #8) 머지 후 아래를 적용한다.

## 1. 서브도메인 A 레코드

가비아 DNS에 추가 (기존 api / dev-api 와 동일 패턴):

    admin        → 1.201.120.148   (prod IP)
    dev-admin    → 139.150.11.145  (dev IP)

## 2. 리버스 프록시 블록

`deploy/gamss-admin.conf`의 server 블록을 `deploy/nginx/conf/`에 추가한다
(dev 서버는 server_name 을 `dev-admin.gamss.kr`로). 실제 admin 인증이 붙기 전까지는
같은 파일 하단 주석의 Basic Auth 또는 IP 허용목록으로 임시 차단한다.

## 3. compose 에 admin 서비스 추가

`deploy/prod/docker-compose.yml`·`deploy/dev/docker-compose.yml`의 services 에:

```yaml
  admin:
    image: ghcr.io/nexters/gamss-server-admin:${IMAGE_TAG:-prod}
    restart: unless-stopped
    networks: [gamss]
    # 포트 미노출 → 앞단 nginx만 접근
```

nginx 서비스의 `depends_on`에 `admin`을 추가한다.

## 4. CD 에 admin 이미지 빌드 추가

`.github/workflows/image.yml`의 `build-and-push` job에 admin 이미지 빌드 스텝을 하나 더 둔다
(앱과 같은 태그 규칙, 이미지명만 `-admin` 접미사):

Firebase 웹 설정은 공개값이라 **빌드 시점에 인라인**된다. build-args 로 넘긴다
(값은 비밀이 아니므로 GitHub Variables 로 두면 된다):

```yaml
      - name: Build and push admin
        uses: docker/build-push-action@v6
        with:
          context: ./admin-web
          push: true
          build-args: |
            VITE_API_BASE_URL=
            VITE_FIREBASE_API_KEY=${{ vars.VITE_FIREBASE_API_KEY }}
            VITE_FIREBASE_AUTH_DOMAIN=${{ vars.VITE_FIREBASE_AUTH_DOMAIN }}
            VITE_FIREBASE_PROJECT_ID=${{ vars.VITE_FIREBASE_PROJECT_ID }}
            VITE_FIREBASE_APP_ID=${{ vars.VITE_FIREBASE_APP_ID }}
          tags: |
            ${{ steps.meta.outputs.image }}-admin:${{ steps.meta.outputs.tag }}
            ${{ steps.meta.outputs.image }}-admin:${{ github.ref_name }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

`VITE_API_BASE_URL` 은 admin.gamss.kr 에서 상대경로(/api)를 쓰므로 비워둔다.

## 5. 관리자 허용목록(ADMIN_EMAILS) 주입

백엔드(app 서비스)가 읽는 값이다. 로그인한 구글 이메일이 이 목록에 있어야 관리자 토큰이 발급된다.
비밀은 아니지만, 기존 `.env` 생성 흐름(DB_PASSWORD·JWT_SECRET)에 얹어 CD가 서버 `.env` 에 써주면 된다:

    ADMIN_EMAILS=a@gmail.com,b@gmail.com

app 서비스 environment 에 `ADMIN_EMAILS: ${ADMIN_EMAILS}` 를 추가한다.
추가/삭제는 값만 고치고 앱을 재시작하면 된다(코드 변경 불필요).

## 6. HTTPS

certbot로 `admin.gamss.kr`(및 `dev-admin`) 인증서 발급 후 443 블록 추가.
관리자 화면이므로 TLS는 필수. (deployment-todo 참고)
```
