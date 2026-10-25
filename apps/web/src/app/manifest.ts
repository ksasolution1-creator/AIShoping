import type { MetadataRoute } from "next";

/** 휴대폰 홈 화면에 추가할 때 쓰는 앱 정보 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "떨고래 마켓 - 재고·땡처리 도매방",
    short_name: "떨고래 마켓",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#1e5bc6",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
