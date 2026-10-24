// 바코드 라벨 인쇄 화면(/labels)에 넘길 목록. 새 탭으로 열기 때문에 sessionStorage 대신 localStorage를 쓴다.

export type LabelItem = {
  skuCode: string;
  productName: string;
  optionText: string;
  gradeLabel: string;
  price: number;
  qty: number;
};

const KEY = "ddgorae:labels";

export function openLabelPrint(items: LabelItem[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(items.filter((i) => i.qty > 0)));
  } catch {
    // 저장소를 못 쓰는 환경(사생활 보호 모드 등)이면 인쇄 화면에서 빈 목록으로 안내한다
  }
  window.open("/labels", "_blank");
}

export function readLabelItems(): LabelItem[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as LabelItem[]) : [];
  } catch {
    return [];
  }
}
