// 전자상거래법상 필수 표기 항목. 실제 사업자 정보로 바꿔야 한다.
export function SiteFooter() {
  return (
    <footer className="border-t border-line bg-surface-muted">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-8 text-xs leading-relaxed text-ink-faint sm:px-6">
        <p className="font-medium text-ink-soft">AIShop</p>
        <p>
          상호: (입력 필요) · 대표: (입력 필요) · 사업자등록번호: (입력 필요) ·
          통신판매업 신고번호: (입력 필요)
        </p>
        <p>주소: (입력 필요) · 고객센터: (입력 필요) · 개인정보관리책임자: (입력 필요)</p>
        <p>
          반품·리퍼·중고 상품은 상품마다 상태등급과 상태 설명을 표시합니다. 수령 후 7일 이내 청약철회가
          가능합니다.
        </p>
      </div>
    </footer>
  );
}
