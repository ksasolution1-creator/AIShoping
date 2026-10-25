import Link from "next/link";

import { WhaleMark } from "./brand/WhaleMark";
import { COMPANY } from "@/lib/company";

// 전자상거래법상 필수 표기 항목 (사업자 정보는 lib/company.ts)
export function SiteFooter() {
  return (
    <footer className="bg-navy text-foam/80">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-10 text-xs leading-relaxed sm:px-6">
        <div className="flex items-center gap-2">
          <WhaleMark className="size-9" />
          <span className="font-display text-lg text-white">{COMPANY.shopName}</span>
          <span className="rounded-full bg-tag px-2 py-0.5 text-[10px] font-bold text-navy">재고 · 땡처리 도매방</span>
        </div>
        <nav className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          <Link href="/terms" className="hover:text-white">
            이용약관
          </Link>
          <Link href="/privacy" className="font-bold text-white">
            개인정보처리방침
          </Link>
          <Link href="/policy" className="hover:text-white">
            배송·교환·환불 안내
          </Link>
        </nav>
        <div className="flex flex-col gap-1">
          <p>
            상호: {COMPANY.name} · 대표: {COMPANY.ceo} · 사업자등록번호: {COMPANY.businessNo}{" "}
            <a href={COMPANY.ftcUrl} target="_blank" rel="noreferrer" className="underline underline-offset-2 hover:text-white">
              사업자정보확인
            </a>{" "}
            · 통신판매업 신고번호: {COMPANY.mailOrderNo}
          </p>
          <p>
            주소: {COMPANY.address} · 개인정보관리책임자: {COMPANY.privacyOfficer} · 호스팅서비스 제공자:{" "}
            {COMPANY.hostingProvider}
          </p>
          <p>
            고객센터: {COMPANY.phone} ({COMPANY.csHours})
            {COMPANY.email && ` · 이메일: ${COMPANY.email}`}
          </p>
        </div>
        {COMPANY.escrow && (
          <p>
            고객님의 안전거래를 위해 현금 결제 시 {COMPANY.escrow} 구매안전서비스를 이용하실 수 있습니다.
          </p>
        )}
        <p className="text-foam/60">
          반품·리퍼·중고 상품은 상품마다 상태등급과 상태 설명을 표시합니다. 수령 후 7일 이내 청약철회가 가능합니다.
        </p>
      </div>
    </footer>
  );
}
