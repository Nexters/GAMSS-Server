import type { MetadataRoute } from "next";

/** 정적 익스포트에서는 이 라우트도 빌드 시점에 한 번만 만들어져야 한다. */
export const dynamic = "force-static";

import { SITE_URL } from "./site";

/**
 * 페이지가 하나뿐이라 항목도 하나다. 이용약관·개인정보처리방침이 생기면 여기에 함께 추가한다.
 *
 * `lastModified` 는 빌드 시각이 아니라 고정값을 쓴다 — 배포할 때마다 날짜가 바뀌면
 * 내용이 그대로인데도 크롤러에 "변경됨"으로 보고하는 셈이 된다.
 *
 * 대신 **실제로 내용을 고친 날**을 적는다. 앞선 날짜를 쓰면 크롤러가 미래의 수정일을
 * 받게 되어 값 자체를 믿지 않는다.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  return [
    {
      url: SITE_URL,
      lastModified: new Date("2026-08-20"),
      changeFrequency: "monthly",
      priority: 1,
    },
  ];
}
