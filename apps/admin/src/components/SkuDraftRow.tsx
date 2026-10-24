"use client";

import { GRADES, parseOptions, type CreateSku, type GradeCode } from "@/lib/api";

export type SkuDraft = {
  key: number;
  conditionGrade: GradeCode | "";
  options: string;
  conditionNote: string;
  barcode: string;
  skuCode: string;
  price: string;
  listPrice: string;
  initialStock: string;
};

let nextKey = 1;

export function emptySku(): SkuDraft {
  return {
    key: nextKey++,
    conditionGrade: "",
    options: "",
    conditionNote: "",
    barcode: "",
    skuCode: "",
    price: "",
    listPrice: "",
    initialStock: "1",
  };
}

export function skuDraftToRequest(d: SkuDraft): CreateSku {
  return {
    skuCode: d.skuCode.trim() || null,
    optionValues: parseOptions(d.options),
    conditionGrade: d.conditionGrade as GradeCode,
    conditionNote: d.conditionNote.trim() || null,
    barcode: d.barcode.trim() || null,
    price: Number(d.price),
    listPrice: d.listPrice ? Number(d.listPrice) : null,
    initialStock: Number(d.initialStock || 0),
  };
}

/** SKU 한 줄 입력. 상태등급이 새상품이 아니면 상태 설명을 쓰도록 안내한다. */
export function SkuDraftRow({
  value,
  onChange,
  onRemove,
  errors = {},
}: {
  value: SkuDraft;
  onChange: (next: SkuDraft) => void;
  onRemove?: () => void;
  errors?: Record<string, string>;
}) {
  const set = <K extends keyof SkuDraft>(key: K, v: SkuDraft[K]) => onChange({ ...value, [key]: v });
  const needsNote = value.conditionGrade !== "" && value.conditionGrade !== "NEW";
  const id = `sku-${value.key}`;

  return (
    <div className="grid gap-2 rounded border border-line bg-surface p-3 sm:grid-cols-6">
      <label className="flex flex-col gap-1 sm:col-span-2">
        <span className="field-label">상태등급 *</span>
        <select
          id={`${id}-grade`}
          className="input"
          value={value.conditionGrade}
          onChange={(e) => set("conditionGrade", e.target.value as GradeCode)}
        >
          <option value="">선택</option>
          {GRADES.map((g) => (
            <option key={g.code} value={g.code}>
              {g.label}
            </option>
          ))}
        </select>
        {errors.conditionGrade && <span className="text-xs text-danger">{errors.conditionGrade}</span>}
      </label>
      <label className="flex flex-col gap-1 sm:col-span-2">
        <span className="field-label">옵션 (예: 색상=블랙, 사이즈=M)</span>
        <input id={`${id}-options`} className="input" value={value.options} onChange={(e) => set("options", e.target.value)} />
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">판매가 *</span>
        <input
          id={`${id}-price`}
          type="number"
          min={0}
          className="input"
          value={value.price}
          onChange={(e) => set("price", e.target.value)}
        />
        {errors.price && <span className="text-xs text-danger">{errors.price}</span>}
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">정가</span>
        <input
          id={`${id}-list`}
          type="number"
          min={0}
          className="input"
          value={value.listPrice}
          onChange={(e) => set("listPrice", e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1 sm:col-span-3">
        <span className="field-label">상태 설명 {needsNote && <span className="text-warn">(반품·중고는 꼭 입력)</span>}</span>
        <input
          id={`${id}-note`}
          className="input"
          value={value.conditionNote}
          placeholder={needsNote ? "예: 개봉 후 1회 사용, 구성품 전체 포함" : ""}
          onChange={(e) => set("conditionNote", e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">바코드</span>
        <input id={`${id}-barcode`} className="input" value={value.barcode} onChange={(e) => set("barcode", e.target.value)} />
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">SKU 코드</span>
        <input
          id={`${id}-code`}
          className="input"
          value={value.skuCode}
          placeholder="자동"
          onChange={(e) => set("skuCode", e.target.value)}
        />
      </label>
      <div className="flex items-end gap-2">
        <label className="flex flex-1 flex-col gap-1">
          <span className="field-label">초기 재고</span>
          <input
            id={`${id}-stock`}
            type="number"
            min={0}
            className="input"
            value={value.initialStock}
            onChange={(e) => set("initialStock", e.target.value)}
          />
        </label>
        {onRemove && (
          <button type="button" className="btn-outline px-2.5 text-danger" onClick={onRemove} aria-label="SKU 삭제">
            ×
          </button>
        )}
      </div>
    </div>
  );
}

export function validateSkuDraft(d: SkuDraft): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!d.conditionGrade) errors.conditionGrade = "상태등급을 선택해 주세요.";
  if (d.price === "" || Number(d.price) < 0) errors.price = "판매가를 입력해 주세요.";
  return errors;
}
