/** 사이트 공통 상수. 메타데이터·robots·sitemap·구조화 데이터가 같은 값을 보게 모아둔다. */
export const SITE_URL = "https://gamss.kr";
export const SITE_NAME = "GAMSS";
export const TAGLINE = "속닥속닥, 너만 알고 있어";
export const DESCRIPTION =
  "누구한테도 말 못 할 이야기를 감정 쓰레기통에 버려보세요. 여섯 감정 친구들이 대신 반응해주고, 하루가 끝나면 감정 카드 한 장으로 남습니다.";

export const APP_STORE_URL = "https://apps.apple.com/app/id6799644564";
export const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.gamss.android";

/**
 * iOS 심사 중 동안 앱스토어 자리를 대신하는 사전 알림 폼.
 *
 * 안드로이드는 스토어가 열렸지만 iOS 는 아직 심사 중이라, [APP_STORE_URL] 을 누르면 없는
 * 페이지가 뜬다. 그 자리를 이 폼으로 대신한다.
 *
 * **심사가 끝나면 이 값을 `null` 로 바꾸면 된다** — 버튼·구조화 데이터·공유 페이지의 iOS 이동이
 * 한꺼번에 앱스토어로 돌아온다. 세 파일을 각각 되짚지 않으려고 한 곳에 모아 뒀다.
 */
export const IOS_PENDING_FORM_URL: string | null =
  "https://docs.google.com/forms/d/e/1FAIpQLSfZiJ64Pkc5DqMYwyOT_0tdR0miCQgLzBUc0dX4xTTOopTJdw/viewform";
