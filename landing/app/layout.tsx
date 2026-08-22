import type { Metadata } from "next";
import "./globals.css";
import {
  APP_STORE_URL,
  DESCRIPTION,
  IOS_PENDING_FORM_URL,
  PLAY_STORE_URL,
  SITE_NAME,
  SITE_URL,
  TAGLINE,
} from "./site";

const TITLE = `${SITE_NAME} · ${TAGLINE}`;

/**
 * 검색·공유 양쪽을 함께 챙긴다.
 *
 * 이 페이지는 인스타 스토리 링크의 도착지이기도 하지만 URL 자체가 메신저로 공유되므로,
 * 미리보기 카드가 곧 첫인상이 된다. OG 이미지를 빼면 회색 빈 상자가 나간다.
 */
export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: TITLE,
  description: DESCRIPTION,
  applicationName: SITE_NAME,
  keywords: [
    "감쓰",
    "GAMSS",
    "감정 쓰레기통",
    "감정 기록",
    "감정 일기",
    "속마음",
    "익명 기록",
    "감정 카드",
  ],
  alternates: { canonical: SITE_URL },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" },
  },
  openGraph: {
    type: "website",
    locale: "ko_KR",
    url: SITE_URL,
    siteName: SITE_NAME,
    title: TITLE,
    description: DESCRIPTION,
    images: [
      {
        url: "/og.png",
        width: 1200,
        height: 630,
        alt: `${SITE_NAME} — ${TAGLINE}`,
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: TITLE,
    description: DESCRIPTION,
    images: ["/og.png"],
  },
  formatDetection: { telephone: false, email: false, address: false },
};

/**
 * 검색엔진이 "앱을 소개하는 페이지"라는 것을 읽을 수 있게 구조화 데이터를 넣는다.
 * 두 스토어 링크를 함께 실어 같은 앱의 서로 다른 배포처임을 알린다.
 */
const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "MobileApplication",
  name: SITE_NAME,
  alternateName: "감쓰",
  description: DESCRIPTION,
  url: SITE_URL,
  image: `${SITE_URL}/og.png`,
  applicationCategory: "LifestyleApplication",
  operatingSystem: "iOS, Android",
  inLanguage: "ko-KR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "KRW" },
  // iOS 심사 중에는 앱스토어를 빼고 알린다 — `sameAs` 는 "이 앱이 있는 곳"이라, 아직 없는
  // 페이지를 가리키면 검색엔진에 죽은 링크를 알리는 셈이 된다. 폼은 스토어가 아니므로 넣지 않는다.
  sameAs: IOS_PENDING_FORM_URL ? [PLAY_STORE_URL] : [APP_STORE_URL, PLAY_STORE_URL],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    /*
     * 번역·색상 추출 같은 브라우저 확장이 <html>·<body> 에 자기 속성을 붙이는 경우가 있어
     * (예: trancy-version, cz-shortcut-listen) 하이드레이션 경고가 뜬다. 우리 마크업 문제가 아니고 막을 수도 없어
     * 이 요소의 속성 불일치만 무시한다 — 자식 트리의 검사는 그대로 살아 있다.
     */
    <html lang="ko" className="h-full antialiased" suppressHydrationWarning>
      {/* 확장 프로그램은 <body> 에도 속성을 붙인다(예: cz-shortcut-listen). 여기도 함께 무시한다. */}
      <body className="flex min-h-full flex-col" suppressHydrationWarning>
        {children}
        <script
          type="application/ld+json"
          // 우리가 만든 상수 객체라 외부 입력이 섞이지 않는다.
          dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }}
        />
      </body>
    </html>
  );
}
