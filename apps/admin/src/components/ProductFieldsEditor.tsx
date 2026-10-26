"use client";

import { useEffect, useState } from "react";

import { api, type AdminProductDetail, type Category, type ProductFields } from "@/lib/api";

/** 입력 중인 값은 문자열로 들고 있다가 저장할 때 숫자로 바꾼다 */
export type ProductDraft = {
  name: string;
  brand: string;
  modelName: string;
  categoryId: string;
  description: string;
  kcCertNo: string;
  /** yyyy-MM-dd, 비우면 소비기한 없음 */
  expiryDate: string;
  shippingFee: string;
  freeShippingThreshold: string;
  notice: { key: string; value: string }[];
};

const DEFAULT_NOTICE = ["제조국", "제조사(수입자)", "품질보증기준", "A/S 책임자와 전화번호"];

export function emptyDraft(): ProductDraft {
  return {
    name: "",
    brand: "",
    modelName: "",
    categoryId: "",
    description: "",
    kcCertNo: "",
    expiryDate: "",
    shippingFee: "3000",
    freeShippingThreshold: "50000",
    notice: DEFAULT_NOTICE.map((key) => ({ key, value: "" })),
  };
}

export function draftFromProduct(p: AdminProductDetail): ProductDraft {
  const notice = Object.entries(p.noticeInfo).map(([key, value]) => ({ key, value }));
  return {
    name: p.name,
    brand: p.brand ?? "",
    modelName: p.modelName ?? "",
    categoryId: p.categoryId ? String(p.categoryId) : "",
    description: p.description ?? "",
    kcCertNo: p.kcCertNo ?? "",
    expiryDate: p.expiryDate ?? "",
    shippingFee: String(p.shippingFee),
    freeShippingThreshold: p.freeShippingThreshold == null ? "" : String(p.freeShippingThreshold),
    notice: notice.length ? notice : DEFAULT_NOTICE.map((key) => ({ key, value: "" })),
  };
}

const nullIfBlank = (s: string) => (s.trim() ? s.trim() : null);

export function draftToFields(d: ProductDraft): ProductFields {
  return {
    name: d.name.trim(),
    brand: nullIfBlank(d.brand),
    modelName: nullIfBlank(d.modelName),
    categoryId: d.categoryId ? Number(d.categoryId) : null,
    description: nullIfBlank(d.description),
    kcCertNo: nullIfBlank(d.kcCertNo),
    expiryDate: d.expiryDate || null,
    shippingFee: Number(d.shippingFee || 0),
    freeShippingThreshold: d.freeShippingThreshold ? Number(d.freeShippingThreshold) : null,
    noticeInfo: Object.fromEntries(
      d.notice.filter((n) => n.key.trim() && n.value.trim()).map((n) => [n.key.trim(), n.value.trim()]),
    ),
  };
}

export function ProductFieldsEditor({
  value,
  onChange,
  errors = {},
}: {
  value: ProductDraft;
  onChange: (next: ProductDraft) => void;
  errors?: Record<string, string>;
}) {
  const [categories, setCategories] = useState<Category[]>([]);
  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  const set = <K extends keyof ProductDraft>(key: K, v: ProductDraft[K]) => onChange({ ...value, [key]: v });
  const err = (field: string) => errors[`product.${field}`] ?? errors[field];
  const byId = new Map(categories.map((c) => [c.id, c]));
  const categoryLabel = (c: Category) => {
    const parent = c.parentId ? byId.get(c.parentId) : undefined;
    return parent ? `${parent.name} > ${c.name}` : c.name;
  };

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="상품명 *" error={err("name")} className="sm:col-span-2">
        <input id="p-name" className="input" value={value.name} onChange={(e) => set("name", e.target.value)} />
      </Field>
      <Field label="브랜드">
        <input id="p-brand" className="input" value={value.brand} onChange={(e) => set("brand", e.target.value)} />
      </Field>
      <Field label="모델명">
        <input
          id="p-model"
          className="input"
          value={value.modelName}
          onChange={(e) => set("modelName", e.target.value)}
        />
      </Field>
      <Field label="카테고리" error={err("categoryId")}>
        <select
          id="p-category"
          className="input"
          value={value.categoryId}
          onChange={(e) => set("categoryId", e.target.value)}
        >
          <option value="">선택 안 함</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {categoryLabel(c)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="KC 인증번호 (전기용품·어린이제품)">
        <input
          id="p-kc"
          className="input"
          value={value.kcCertNo}
          onChange={(e) => set("kcCertNo", e.target.value)}
        />
      </Field>
      <Field label="소비기한 (식품·건강식품·화장품 등)" error={err("expiryDate")}>
        <div className="flex gap-2">
          <input
            id="p-expiry"
            type="date"
            className="input flex-1"
            value={value.expiryDate}
            onChange={(e) => set("expiryDate", e.target.value)}
          />
          {value.expiryDate && (
            <button type="button" className="btn-outline px-2.5" onClick={() => set("expiryDate", "")}>
              지우기
            </button>
          )}
        </div>
      </Field>
      <Field label="배송비 (원) *" error={err("shippingFee")}>
        <input
          id="p-shipping"
          type="number"
          min={0}
          className="input"
          value={value.shippingFee}
          onChange={(e) => set("shippingFee", e.target.value)}
        />
      </Field>
      <Field label="무료배송 기준 금액 (원)" error={err("freeShippingThreshold")}>
        <input
          id="p-free"
          type="number"
          min={0}
          className="input"
          value={value.freeShippingThreshold}
          onChange={(e) => set("freeShippingThreshold", e.target.value)}
          placeholder="비우면 항상 배송비 부과"
        />
      </Field>
      <Field label="상품 설명" className="sm:col-span-2">
        <textarea
          id="p-desc"
          rows={5}
          className="input h-auto py-2"
          value={value.description}
          onChange={(e) => set("description", e.target.value)}
        />
      </Field>

      <fieldset className="flex flex-col gap-2 sm:col-span-2">
        <legend className="field-label mb-1">상품정보 제공고시 (빈 값은 저장하지 않음)</legend>
        {value.notice.map((row, i) => (
          <div key={i} className="grid grid-cols-[minmax(0,10rem)_minmax(0,1fr)_auto] gap-2">
            <input
              aria-label="항목"
              className="input"
              value={row.key}
              onChange={(e) =>
                set("notice", value.notice.map((n, j) => (j === i ? { ...n, key: e.target.value } : n)))
              }
            />
            <input
              aria-label="내용"
              className="input"
              value={row.value}
              onChange={(e) =>
                set("notice", value.notice.map((n, j) => (j === i ? { ...n, value: e.target.value } : n)))
              }
            />
            <button
              type="button"
              className="btn-outline px-2.5"
              aria-label="항목 삭제"
              onClick={() => set("notice", value.notice.filter((_, j) => j !== i))}
            >
              ×
            </button>
          </div>
        ))}
        <button
          type="button"
          className="btn-outline self-start"
          onClick={() => set("notice", [...value.notice, { key: "", value: "" }])}
        >
          + 항목 추가
        </button>
      </fieldset>
    </div>
  );
}

export function Field({
  label,
  error,
  className = "",
  children,
}: {
  label: string;
  error?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}
