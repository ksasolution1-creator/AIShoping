"use client";

import JsBarcode from "jsbarcode";
import { useEffect, useRef, useState } from "react";

import { formatWon } from "@/lib/api";
import { type LabelItem, readLabelItems } from "@/lib/labels";

// 감열 라벨프린터에서 많이 쓰는 크기 (가로 x 세로 mm)
const SIZES = [
  { id: "40x30", w: 40, h: 30 },
  { id: "50x30", w: 50, h: 30 },
  { id: "60x40", w: 60, h: 40 },
  { id: "40x20", w: 40, h: 20 },
] as const;

const SIZE_KEY = "ddgorae:label-size";

/**
 * 바코드 라벨 인쇄. 라벨 한 장이 인쇄 한 페이지가 되도록 @page 크기를 라벨 크기로 맞춘다.
 * 프린터 설정에서 용지를 같은 크기로, 여백을 '없음'으로 두면 된다.
 */
export function LabelSheet() {
  const [items, setItems] = useState<LabelItem[]>(() => (typeof window === "undefined" ? [] : readLabelItems()));
  const [sizeId, setSizeId] = useState<string>(() => {
    try {
      return localStorage.getItem(SIZE_KEY) ?? "40x30";
    } catch {
      return "40x30";
    }
  });
  const [showPrice, setShowPrice] = useState(true);
  const size = SIZES.find((s) => s.id === sizeId) ?? SIZES[0];

  function changeSize(id: string) {
    setSizeId(id);
    try {
      localStorage.setItem(SIZE_KEY, id);
    } catch {
      // 저장 못 해도 이번 인쇄에는 문제없다
    }
  }

  const labels = items.flatMap((item) => Array.from({ length: item.qty }, (_, i) => ({ item, key: `${item.skuCode}-${i}` })));
  const total = labels.length;

  return (
    <div>
      <style>{`
        @page { size: ${size.w}mm ${size.h}mm; margin: 0; }
        @media print {
          body { background: white; }
          .no-print { display: none !important; }
          .label { break-after: page; }
        }
      `}</style>

      <div className="no-print mx-auto flex max-w-3xl flex-col gap-4 p-6">
        <h1 className="text-xl font-bold">바코드 라벨 인쇄</h1>
        <div className="card flex flex-wrap items-end gap-4 p-4">
          <label className="flex flex-col gap-1">
            <span className="field-label">라벨 크기</span>
            <select value={sizeId} onChange={(e) => changeSize(e.target.value)} className="input w-36">
              {SIZES.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.w} x {s.h} mm
                </option>
              ))}
            </select>
          </label>
          <label className="flex h-9 items-center gap-2 text-sm">
            <input type="checkbox" checked={showPrice} onChange={(e) => setShowPrice(e.target.checked)} />
            판매가 표시
          </label>
          <button type="button" className="btn-primary ml-auto" disabled={total === 0} onClick={() => window.print()}>
            {total.toLocaleString("ko-KR")}장 인쇄
          </button>
        </div>
        <p className="text-xs text-ink-soft">
          프린터 설정에서 용지 크기를 {size.w} x {size.h} mm, 여백을 &lsquo;없음&rsquo;, 배율을 100%로 맞춰 주세요. 바코드에는 SKU코드가
          들어가며, 재고·매입 화면 검색칸에서 스캐너로 바로 찾을 수 있습니다.
        </p>

        {items.length === 0 ? (
          <p className="card p-8 text-center text-sm text-ink-faint">
            인쇄할 라벨이 없습니다. 재고 현황에서 상품을 선택하거나 입고한 매입서에서 라벨 인쇄를 눌러 주세요.
          </p>
        ) : (
          <ul className="card divide-y divide-line">
            {items.map((item, index) => (
              <li key={item.skuCode} className="flex items-center gap-3 px-4 py-2 text-sm">
                <span className="flex-1">
                  {item.productName}
                  <span className="ml-2 text-xs text-ink-soft">
                    {item.gradeLabel}
                    {item.optionText && ` · ${item.optionText}`}
                  </span>
                  <span className="ml-2 font-mono text-xs text-ink-faint">{item.skuCode}</span>
                </span>
                <label className="flex items-center gap-1 text-xs text-ink-soft">
                  장수
                  <input
                    type="number"
                    min={0}
                    max={999}
                    value={item.qty}
                    onChange={(e) =>
                      setItems((prev) =>
                        prev.map((x, i) => (i === index ? { ...x, qty: Math.min(999, Math.max(0, Number(e.target.value) || 0)) } : x)),
                      )
                    }
                    className="input h-8 w-20 text-right"
                  />
                </label>
              </li>
            ))}
          </ul>
        )}

        {labels.length > 0 && <p className="text-xs text-ink-faint">미리보기 (첫 장)</p>}
      </div>

      <div className="flex flex-wrap justify-center gap-4 print:block">
        {labels.map(({ item, key }, i) => (
          <Label key={key} item={item} w={size.w} h={size.h} showPrice={showPrice} className={i === 0 ? "" : "hidden print:flex"} />
        ))}
      </div>
    </div>
  );
}

function Label({
  item,
  w,
  h,
  showPrice,
  className,
}: {
  item: LabelItem;
  w: number;
  h: number;
  showPrice: boolean;
  className: string;
}) {
  const svgRef = useRef<SVGSVGElement>(null);
  const compact = h <= 20;

  useEffect(() => {
    if (!svgRef.current) return;
    JsBarcode(svgRef.current, item.skuCode, {
      format: "CODE128",
      displayValue: false,
      margin: 0,
      height: 40,
      width: 2,
    });
  }, [item.skuCode]);

  return (
    <div
      className={`label flex flex-col overflow-hidden bg-white text-black outline outline-1 outline-line print:outline-0 ${className}`}
      style={{ width: `${w}mm`, height: `${h}mm`, padding: "1.5mm 2mm", fontSize: compact ? "6pt" : "7pt", lineHeight: 1.2 }}
    >
      <div className="truncate font-bold" style={{ fontSize: compact ? "6.5pt" : "8pt" }}>
        {item.productName}
      </div>
      {!compact && (
        <div className="truncate">
          {item.gradeLabel}
          {item.optionText && ` · ${item.optionText}`}
        </div>
      )}
      <svg ref={svgRef} className="min-h-0 w-full flex-1" preserveAspectRatio="none" aria-label={`바코드 ${item.skuCode}`} />
      <div className="flex items-baseline justify-between font-mono">
        <span>{item.skuCode}</span>
        {showPrice && item.price > 0 && <span className="font-sans font-bold">{formatWon(item.price)}</span>}
      </div>
    </div>
  );
}
