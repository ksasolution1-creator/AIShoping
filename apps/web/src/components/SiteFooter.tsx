import { WhaleMark } from "./brand/WhaleMark";

// 전자상거래법상 필수 표기 항목
export function SiteFooter() {
  return (
    <footer className="bg-navy text-foam/80">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-10 text-xs leading-relaxed sm:px-6">
        <div className="flex items-center gap-2">
          <WhaleMark className="size-9" />
          <span className="font-display text-lg text-white">떨고래 마켓</span>
          <span className="rounded-full bg-tag px-2 py-0.5 text-[10px] font-bold text-navy">재고 · 땡처리 도매방</span>
        </div>
        <div className="flex flex-col gap-1">
          <p>
            상호: (주)케이에스에이솔루션 · 대표: 김민혁 · 사업자등록번호: 751-88-00289 · 통신판매업 신고번호:
            2025-서울금천-1886
          </p>
          <p>
            주소: 서울특별시 금천구 벚꽃로 286, 삼성리더스타워 12층 1211호 (가산동) · 고객센터: 010-5275-3772 ·
            개인정보관리책임자: 김민혁
          </p>
        </div>
        <p className="text-foam/60">
          반품·리퍼·중고 상품은 상품마다 상태등급과 상태 설명을 표시합니다. 수령 후 7일 이내 청약철회가 가능합니다.
        </p>
      </div>
    </footer>
  );
}
