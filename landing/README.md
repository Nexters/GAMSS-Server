# GAMSS 랜딩

`gamss.kr` 에 뜨는 랜딩 페이지. 공유 링크를 눌렀는데 앱이 없는 사람이 도착하는 곳이다.

## 개발

```bash
npm ci
npm run dev      # http://localhost:3000
```

## 빌드

```bash
npm run build    # out/ 에 정적 파일이 떨어진다
```

**`npm start` 는 없다.** `next.config.ts` 가 `output: 'export'` 라 서버가 없고,
`next start` 는 이 설정에서 명시적으로 실패한다. 빌드 결과를 미리 보려면 정적 서버를
따로 띄운다(`npx serve out`).

## 배포

CI 가 대신한다. 직접 올릴 일은 없다.

`.github/workflows/image.yml` 의 `deploy` 잡이 여기서 `npm run build` 를 돌리고
`out/` 을 `deploy/nginx/www` 에 합친 뒤, 그 디렉터리를 서버로 통째로 옮긴다.
nginx 가 정적으로 내려주므로 **Node 런타임이 서버에 없다.**

### 왜 정적 익스포트인가

같은 디렉터리에 딥링크 검증 파일(`.well-known/apple-app-site-association`,
`assetlinks.json`)이 함께 있다. 이 경로는 **앱 컨테이너의 배포·장애와 무관하게 살아
있어야 한다** — 검증이 한 번 실패하면 OS 가 한동안 다시 물어보지 않아 링크가 조용히
죽는다. Node 런타임을 붙이면 그 성질이 깨진다
(`deploy/nginx/conf/gamss.conf` 상단 참고).

배포 잡에는 검증 파일이 살아남았는지 확인하는 `test -f` 세 줄이 있다. 합치는 방식을
바꿀 때 그 줄을 지우지 말 것.

## 폰트

Pretendard 를 CDN 없이 self-host 한다. 인스타 인앱 브라우저처럼 환경이 제각각인 곳에서
열리는 페이지라 외부 요청을 두지 않는다. `public/fonts/` 의 dynamic subset 이라
브라우저가 실제로 쓰인 글자 조각만 받는다. 라이선스는 `public/fonts/OFL.txt`.

## 디자인 자원

`design/` 에 캐릭터·로고 SVG 와 색·타이포 규격이 있다. 색은 팔레트 문서가 아니라
**에셋에 박힌 값을 읽어 적은 것**이라 그쪽이 정답이다(`design/colors.md`).
