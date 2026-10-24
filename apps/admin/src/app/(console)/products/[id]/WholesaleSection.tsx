"use client";

import { useState } from "react";

import { api, ApiError, formatWon, type AdminProductDetail } from "@/lib/api";

type TierDraft = { minQty: string; percent: string };

/** 도매 구매조건: 최소구매수량, 출고 예정일, 수량별 할인 */
export function WholesaleSection({
  product,
  onSaved,
  show,
}: {
  product: AdminProductDetail;
  onSaved: (p: AdminProductDetail) => void;
  show: (kind: "ok" | "error", text: string) => void;
}) {
  const w = product.wholesale;
  const [minOrderQty, setMinOrderQty] = useState(String(w.minOrderQty));
  const [shipWithinDays, setShipWithinDays] = useState(String(w.shipWithinDays));
  const [tiers, setTiers] = useState<TierDraft[]>(
    w.tiers.map((t) => ({ minQty: String(t.minQty), percent: String(t.discountBp / 100) })),
  );
  const [saving, setSaving] = useState(false);
  const cheapest = Math.min(...product.skus.map((s) => s.price));

  async function save() {
    setSaving(true);
    try {
      const saved = await api.updateWholesale(product.id, {
        minOrderQty: Number(minOrderQty) || 1,
        shipWithinDays: Number(shipWithinDays) || 0,
        tiers: tiers
          .filter((t) => t.minQty && t.percent)
          .map((t) => ({ minQty: Number(t.minQty), discountBp: Math.round(Number(t.percent) * 100) })),
      });
      onSaved(saved);
      show("ok", "도매 조건을 저장했습니다.");
    } catch (e) {
      show("error", e instanceof ApiError ? (Object.values(e.fieldErrors)[0] ?? e.message) : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card flex flex-col gap-4 p-5">
      <div>
        <h2 className="font-semibold">도매 조건</h2>
        <p className="text-xs text-ink-soft">
          수량은 같은 상품의 상태·옵션을 모두 합쳐서 셉니다. 예: 반품 5개 + 리퍼 5개 = 10개.
        </p>
      </div>
      <div className="flex flex-wrap gap-4">
        <label className="flex flex-col gap-1">
          <span className="field-label">최소구매수량</span>
          <span className="flex items-center gap-1 text-sm">
            <input
              type="number"
              min={1}
              value={minOrderQty}
              onChange={(e) => setMinOrderQty(e.target.value)}
              className="input w-24 text-right"
            />
            개부터
          </span>
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">출고 예정</span>
          <span className="flex items-center gap-1 text-sm">
            결제 후
            <input
              type="number"
              min={0}
              max={30}
              value={shipWithinDays}
              onChange={(e) => setShipWithinDays(e.target.value)}
              className="input w-20 text-right"
            />
            일 이내 (0 = 당일)
          </span>
        </label>
      </div>

      <div className="flex flex-col gap-2">
        <span className="field-label">수량별 할인</span>
        {tiers.map((t, i) => {
          const bp = Math.round(Number(t.percent) * 100) || 0;
          const unit = Number.isFinite(cheapest) ? cheapest - Math.floor((cheapest * bp) / 10_000 / 10) * 10 : null;
          return (
            <div key={i} className="flex flex-wrap items-center gap-2 text-sm">
              <input
                type="number"
                min={2}
                value={t.minQty}
                aria-label={`${i + 1}단계 수량`}
                onChange={(e) => setTiers((prev) => prev.map((x, j) => (j === i ? { ...x, minQty: e.target.value } : x)))}
                className="input w-24 text-right"
              />
              개 이상
              <input
                type="number"
                min={0.01}
                max={90}
                step={0.5}
                value={t.percent}
                aria-label={`${i + 1}단계 할인율`}
                onChange={(e) => setTiers((prev) => prev.map((x, j) => (j === i ? { ...x, percent: e.target.value } : x)))}
                className="input w-20 text-right"
              />
              % 할인
              {unit != null && bp > 0 && (
                <span className="text-xs text-ink-faint">(가장 싼 상태 기준 개당 {formatWon(unit)})</span>
              )}
              <button
                type="button"
                onClick={() => setTiers((prev) => prev.filter((_, j) => j !== i))}
                aria-label={`${i + 1}단계 빼기`}
                className="rounded px-2 py-1 text-ink-faint hover:bg-surface-muted hover:text-danger"
              >
                ✕
              </button>
            </div>
          );
        })}
        {tiers.length < 10 && (
          <button
            type="button"
            onClick={() => setTiers((prev) => [...prev, { minQty: "", percent: "" }])}
            className="self-start text-sm text-brand underline underline-offset-4"
          >
            + 할인 단계 추가
          </button>
        )}
      </div>
      <button type="button" className="btn-primary self-end" disabled={saving} onClick={save}>
        도매 조건 저장
      </button>
    </section>
  );
}
