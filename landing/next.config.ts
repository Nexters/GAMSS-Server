import type { NextConfig } from "next";

/**
 * 정적 익스포트로 빌드한다. 산출물(`out/`)을 nginx 가 그대로 내려주므로,
 * 랜딩과 딥링크 검증 파일(`/.well-known/`)이 앱 컨테이너의 배포·장애와 무관하게 살아 있다.
 * Node 런타임을 붙이면 그 성질이 깨진다(`deploy/nginx/conf/gamss.conf` 상단 참고).
 */
const nextConfig: NextConfig = {
  output: "export",
  // 이미지 최적화는 서버가 필요하다. 정적 익스포트에서는 끈다.
  images: { unoptimized: true },
};

export default nextConfig;
