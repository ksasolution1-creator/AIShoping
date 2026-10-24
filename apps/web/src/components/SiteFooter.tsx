import { WhaleMark } from "./brand/WhaleMark";

// 전자상거래법상 필수 표기 항목. 실제 사업자 정보로 바꿔야 한다.
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
            상호: (입력 필요) · 대표: (입력 필요) · 사업자등록번호: (입력 필요) · 통신판매업 신고번호: (입력 필요)
          </p>
          <p>주소: (입력 필요) · 고객센터: (입력 필요) · 개인정보관리책임자: (입력 필요)</p>
        </div>
        <p className="text-foam/60">
          반품·리퍼·중고 상품은 상품마다 상태등급과 상태 설명을 표시합니다. 수령 후 7일 이내 청약철회가 가능합니다.
        </p>
      </div>
    </footer>
  );
}
