import type { Metadata } from "next";
import { DESCRIPTION, SITE_NAME } from "../site";

/**
 * 공유 카드 페이지의 메타데이터.
 *
 * **검색에 잡히면 안 된다.** 이 주소들은 사용자가 링크를 받은 사람에게만 준 것이라, 색인되면
 * 토큰을 모르는 사람도 검색으로 남의 감정 기록에 닿는다. `robots.txt` 의 `Disallow` 와 함께
 * 페이지 자체에도 박아 둔다 — 링크가 어딘가에 걸리면 크롤러는 robots.txt 와 무관하게 찾아온다.
 *
 * 미리보기(OG)는 아직 카드별로 만들지 않는다. 카톡에 붙이면 카드 대신 서비스 소개가 뜬다.
 */
export const metadata: Metadata = {
  title: `감정 카드 · ${SITE_NAME}`,
  description: DESCRIPTION,
  robots: { index: false, follow: false, nocache: true },
};

export default function SharedCardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
