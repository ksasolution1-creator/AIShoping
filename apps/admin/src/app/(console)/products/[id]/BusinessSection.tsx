"use client";

import { useState } from "react";

import { ApiError, businessAdminApi, formatWon, gradeLabel, type AdminProductDetail } from "@/lib/api";

/** 사업자 조건: 사업자 전용 판매, 상세이미지 사용 허용, SKU별 사업자 회원가 */
export function BusinessSection({
  product,
  onSaved,
  show,
}: {
  product: AdminProductDetail;
  onSaved: (p: AdminProductDetail) => void;
  show: (kind: "ok" | "error", text: string) => void;
}) {
  const [businessOnly, setBusinessOnly] = useState(product.businessOnly);
  const [imageReuseAllowed, setImageReuseAllowed] = useState(product.imageReuseAllowed);
  const [prices, setPrices] = useState<Record<number, string>>(
    Object.fromEntries(product.skus.map((s) => [s.id, s.businessPrice == null ? "" : String(s.businessPrice)])),
  );
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      const saved = await businessAdminApi.updateProductTerms(product.id, {
        businessOnly,
        imageReuseAllowed,
        skuPrices: product.skus.map((s) => ({
          skuId: s.id,
          businessPrice: prices[s.id] === "" || prices[s.id] == null ? null : Number(prices[s.id]),
        })),
      });
      onSaved(saved);
      show("ok", "사업자 조건을 저장했습니다.");
    } catch (e) {
      show("error", e instanceof ApiError ? (Object.values(e.fieldErrors)[0] ?? e.message) : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card flex flex-col gap-4 p-5">
      <div>
        <h2 className="font-semibold">사업자 조건</h2>
        <p className="text-xs text-ink-soft">사업자 회원가는 운영자가 승인한 사업자 회원에게만 적용됩니다. 비우면 일반 판매가로 팝니다.</p>
      </div>
      <div className="flex flex-wrap gap-6 text-sm">
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={businessOnly} onChange={(e) => setBusinessOnly(e.target.checked)} />
          사업자 회원만 구매 가능
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={imageReuseAllowed} onChange={(e) => setImageReuseAllowed(e.target.checked)} />
          상세이미지 사용 허용 (재판매용)
        </label>
      </div>
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-ink-soft">
          <tr>
            <th className="py-1 font-medium">SKU</th>
            <th className="py-1 text-right font-medium">일반 판매가</th>
            <th className="w-40 py-1 text-right font-medium">사업자 회원가</th>
          </tr>
        </thead>
        <tbody>
          {product.skus.map((s) => (
            <tr key={s.id} className="border-t border-line">
              <td className="py-2">
                <span className="font-mono text-xs text-ink-faint">{s.skuCode}</span> {gradeLabel(s.grade.code)}
              </td>
              <td className="py-2 text-right tabular-nums">{formatWon(s.price)}</td>
              <td className="py-2 text-right">
                <input
                  type="number"
                  min={0}
                  step={10}
                  value={prices[s.id] ?? ""}
                  placeholder="없음"
                  aria-label={`${s.skuCode} 사업자 회원가`}
                  onChange={(e) => setPrices((prev) => ({ ...prev, [s.id]: e.target.value }))}
                  className="input w-36 text-right"
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button type="button" className="btn-primary self-end" disabled={saving} onClick={save}>
        사업자 조건 저장
      </button>
    </section>
  );
}
