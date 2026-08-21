import type { MetadataRoute } from "next";

/** 정적 익스포트에서는 이 라우트도 빌드 시점에 한 번만 만들어져야 한다. */
export const dynamic = "force-static";

import { SITE_URL } from "./site";

/** 정적 익스포트에서도 `out/robots.txt` 로 떨어진다. */
export default function robots(): MetadataRoute.Robots {
  return {
    // /c/ 는 공유 링크를 받은 사람만 아는 주소다. 색인되면 토큰을 모르는 사람도 검색으로
    // 남의 감정 기록에 닿는다(페이지 자체에도 noindex 를 박아 둔다 - app/c/layout.tsx).
    rules: { userAgent: "*", allow: "/", disallow: "/c/" },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
