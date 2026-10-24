import path from "node:path";

import type { NextConfig } from "next";

// 브라우저는 같은 도메인의 /api, /files로 요청하고 Next.js가 API 서버로 넘긴다 (CORS 불필요)
const apiUrl = process.env.API_URL ?? "http://localhost:18080";

const nextConfig: NextConfig = {
  // 운영 배포: node server.js 하나로 실행되는 최소 묶음 생성 (모노레포 루트 기준으로 의존 파일 추적)
  output: "standalone",
  outputFileTracingRoot: path.resolve(process.cwd(), "../.."),
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiUrl}/api/:path*` },
      { source: "/files/:path*", destination: `${apiUrl}/files/:path*` },
    ];
  },
};

export default nextConfig;
