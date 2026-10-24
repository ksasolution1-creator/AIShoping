import type { Metadata } from "next";
import { IBM_Plex_Sans_KR } from "next/font/google";

import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";
import "./globals.css";

const plexKr = IBM_Plex_Sans_KR({
  variable: "--font-plex-kr",
  weight: ["400", "500", "700"],
  subsets: ["latin"],
  preload: false,
});

export const metadata: Metadata = {
  title: {
    default: "AIShop - 반품·리퍼·재고 아울렛",
    template: "%s | AIShop",
  },
  description: "검수한 반품·리퍼·재고 상품을 상태등급과 실물 사진으로 확인하고 저렴하게 구매하세요.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plexKr.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <SiteHeader />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16 sm:px-6">
          {children}
        </main>
        <SiteFooter />
      </body>
    </html>
  );
}
