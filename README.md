# 🤫 GAMSS-Server

> **속닥속닥, 너만 알고 있어**
>
> 나만의 감정 쓰레기통 서비스의 백엔드 레포지토리

![CI](https://github.com/Nexters/GAMSS-Server/actions/workflows/ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)

---

## 📖 소개

일기가 아닌 **감정 쓰레기통**. 사용자가 감정을 정제 없이 한 줄로 남기면, 뚜렷한 성격의 **감정 캐릭터 6종**(기쁨·분노·불안·까칠·다정·엉뚱)이 랜덤하게 반응·상호작용합니다. 대화가 종료되면 **카드**로
저장되어 캘린더에서 회고하거나 공유할 수 있습니다.

> 📌 자세한 기획은 **[서비스 기획 위키](https://github.com/Nexters/GAMSS-Server/wiki/서비스-기획)** 참고

---

## 🛠️ 기술 스택

| 분류       | 스택                        |
|----------|---------------------------|
| 언어 · 런타임 | Kotlin 2.3 · Java 21      |
| 프레임워크    | Spring Boot 4.1 (Web MVC) |
| 빌드       | Gradle (Kotlin DSL)       |
| 코드 품질    | ktlint · Jacoco           |
| CI/CD    | GitHub Actions            |

- 백엔드 상세 → **[기술 스택 위키](https://github.com/Nexters/GAMSS-Server/wiki/기술-스택)**
- 관리자 도구(백오피스) → **[백오피스 위키](https://github.com/Nexters/GAMSS-Server/wiki/백오피스)**

---

## 📂 프로젝트 구성

```
GAMSS-Server/
├─ src/main/kotlin/...   # Spring 백엔드 (고객 API + 관리자 API)
└─ admin-web/            # 백오피스 프론트 (예정 · Refine + shadcn/ui)
```

---

## 🌿 개발 컨벤션

| 구분    | 규칙                                                | 문서                                                            |
|-------|---------------------------------------------------|---------------------------------------------------------------|
| 브랜치   | `prod`(운영) ← `dev`(개발) ← 작업 브랜치                   | [브랜치 전략](https://github.com/Nexters/GAMSS-Server/wiki/브랜치-전략) |
| 커밋    | `type: 설명` (AngularJS, 한글)                        | [커밋 컨벤션](https://github.com/Nexters/GAMSS-Server/wiki/커밋-컨벤션) |
| PR 제목 | `[태그] 내용` (`feat` `fix` `chore` `doc` `refactor`) | [PR 컨벤션](https://github.com/Nexters/GAMSS-Server/wiki/PR-컨벤션) |
| 이슈    | Issue Forms 5종 사용                                 | [이슈 컨벤션](https://github.com/Nexters/GAMSS-Server/wiki/이슈-컨벤션) |

- 머지 방식: `dev` = Squash / `prod` = Merge commit
- `dev → prod` 머지 시 `vX.Y.Z` 릴리스 자동 생성

---

## 📚 문서

전체 문서는 **[프로젝트 위키](https://github.com/Nexters/GAMSS-Server/wiki)** 에서 확인할 수 있습니다.

- [서비스 기획](https://github.com/Nexters/GAMSS-Server/wiki/서비스-기획)
- [기술 스택](https://github.com/Nexters/GAMSS-Server/wiki/기술-스택)
- [백오피스](https://github.com/Nexters/GAMSS-Server/wiki/백오피스)
- [브랜치 전략](https://github.com/Nexters/GAMSS-Server/wiki/브랜치-전략) · [커밋](https://github.com/Nexters/GAMSS-Server/wiki/커밋-컨벤션) · [PR](https://github.com/Nexters/GAMSS-Server/wiki/PR-컨벤션) · [이슈](https://github.com/Nexters/GAMSS-Server/wiki/이슈-컨벤션)
  컨벤션
