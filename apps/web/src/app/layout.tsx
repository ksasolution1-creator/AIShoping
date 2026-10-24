import type { Metadata } from "next";
import { IBM_Plex_Sans_KR, Jua } from "next/font/google";

import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";
import "./globals.css";

const plexKr = IBM_Plex_Sans_KR({
  variable: "--font-plex-kr",
  weight: ["400", "500", "700"],
  subsets: ["latin"],
  preload: false,
});

// 로고·제목용 둥근 글꼴
const jua = Jua({
  variable: "--font-jua",
  weight: "400",
  subsets: ["latin"],
  preload: false,
});

export const metadata: Metadata = {
  title: {
    default: "떨고래 마켓 - 재고·땡처리 도매방",
    template: "%s | 떨고래 마켓",
  },
  description: "검수한 재고·반품·리퍼 상품을 상태등급과 실물 사진으로 확인하고 땡처리 가격에 구매하세요.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plexKr.variable} ${jua.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <SiteHeader />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16 sm:px-6">{children}</main>
        <SiteFooter />
      </body>
    </html>
  );
}
