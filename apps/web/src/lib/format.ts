const won = new Intl.NumberFormat("ko-KR");

export function formatWon(amount: number) {
  return `${won.format(amount)}원`;
}

/** 정가 대비 할인율(%). 정가가 없거나 판매가가 더 높으면 null */
export function discountRate(price: number, listPrice: number | null) {
  if (!listPrice || listPrice <= price) return null;
  return Math.floor(((listPrice - price) / listPrice) * 100);
}

export function optionText(optionValues: Record<string, string>) {
  return Object.entries(optionValues)
    .map(([key, value]) => `${key} ${value}`)
    .join(" · ");
}
