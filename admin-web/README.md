# GAMSS Admin (admin-web)

GAMSS 백오피스(관리자 콘솔). 회원 조회·관리와 통계를 제공하는 SPA다.

## 스택

React + TypeScript + Vite · [Refine](https://refine.dev) · Tailwind + shadcn/ui · recharts · Firebase(구글 로그인)

## 로컬 실행

레포 루트에서 한 번에 띄우기(MySQL + 백엔드 + 프론트):

```bash
./admin-web/dev.sh
```

프론트만 따로 띄우려면(백엔드가 이미 떠 있을 때):

```bash
npm install
npm run dev   # http://localhost:5173, /api 는 localhost:8080 으로 프록시
```

## 환경 변수

`.env.example` 을 `.env.local` 로 복사해 채운다(`.env.local` 은 git 미추적).

- **로컬 개발**: `VITE_AUTH_MODE=mock` + `VITE_DEV_ADMIN_EMAIL=<허용목록 이메일>` → Firebase 없이 백엔드 dev-login 으로 로그인.
- **배포**: `VITE_AUTH_MODE` 를 비우고 `VITE_FIREBASE_*`(구글 로그인용 웹 설정) 채움. `VITE_API_BASE_URL` 은 같은 도메인 서빙 시 비워둠.

관리자 접근 허용목록은 백엔드 `ADMIN_EMAILS`(env)로 관리한다.

## 스크립트

- `npm run dev` — 개발 서버
- `npm run build` — 타입체크 + 프로덕션 빌드
- `npm run lint` — oxlint

## 배포

정적 빌드를 nginx로 서빙한다. 컨테이너·리버스 프록시·CD 연동은 `deploy/README.md` 참고.
