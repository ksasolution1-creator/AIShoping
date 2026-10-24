import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: { default: "떨고래 마켓 판매관리", template: "%s | 떨고래 마켓 판매관리" },
  description: "주문·재고·배송 관리",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
