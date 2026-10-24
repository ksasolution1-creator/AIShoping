import type { NextConfig } from "next";

// 브라우저는 같은 도메인의 /api, /files로 요청하고 Next.js가 API 서버로 넘긴다 (CORS 불필요)
const apiUrl = process.env.API_URL ?? "http://localhost:18080";

const nextConfig: NextConfig = {
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
