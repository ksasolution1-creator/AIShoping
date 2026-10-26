const won = new Intl.NumberFormat("ko-KR");

export function formatWon(amount: number) {
  return `${won.format(amount)}원`;
}

/** 정가 대비 할인율(%). 정가가 없거나 판매가가 더 높으면 null */
export function discountRate(price: number, listPrice: number | null) {
  if (!listPrice || listPrice <= price) return null;
  return Math.floor(((listPrice - price) / listPrice) * 100);
}

/** 할인율(bp, 1bp = 0.01%)을 % 글자로. 예: 500 → "5%", 250 → "2.5%" */
export function formatRate(bp: number) {
  return `${Number((bp / 100).toFixed(2))}%`;
}

/** 수량별 할인 단가에서 빠지는 금액. 서버(WholesaleTerms.unitDiscount)와 같이 10원 단위로 내린다 */
export function tierUnitDiscount(price: number, bp: number) {
  return Math.floor((price * bp) / 10_000 / 10) * 10;
}

/** 합계 수량에 맞는 할인율(bp) */
export function tierBpFor(tiers: { minQty: number; discountBp: number }[], qty: number) {
  return tiers.reduce((bp, t) => (qty >= t.minQty ? t.discountBp : bp), 0);
}

/** 결제 후 출고 안내 문구 */
export function shipWithinText(days: number) {
  return days === 0 ? "결제 당일 출고" : `결제 후 ${days}일 이내 출고`;
}

/** 2027-03-01 → 2027.03.01 */
export function formatDate(isoDate: string) {
  return isoDate.replaceAll("-", ".");
}

export function optionText(optionValues: Record<string, string>) {
  return Object.entries(optionValues)
    .map(([key, value]) => `${key} ${value}`)
    .join(" · ");
}
