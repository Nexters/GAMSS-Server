import type { MetadataRoute } from "next";

/** 정적 익스포트에서는 이 라우트도 빌드 시점에 한 번만 만들어져야 한다. */
export const dynamic = "force-static";

import { SITE_URL } from "./site";

/** 정적 익스포트에서도 `out/robots.txt` 로 떨어진다. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
