import type { Metadata } from "next";
import { IBM_Plex_Sans_KR } from "next/font/google";

import "./globals.css";

const plexKr = IBM_Plex_Sans_KR({
  variable: "--font-plex-kr",
  weight: ["400", "500", "700"],
  subsets: ["latin"],
  preload: false,
});

export const metadata: Metadata = {
  title: { default: "떨고래 마켓 판매관리", template: "%s | 떨고래 마켓 판매관리" },
  description: "주문·재고·배송 관리",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plexKr.variable} h-full antialiased`}>
      <body className="min-h-full">{children}</body>
    </html>
  );
}
