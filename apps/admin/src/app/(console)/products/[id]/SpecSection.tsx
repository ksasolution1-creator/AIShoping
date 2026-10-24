"use client";

import { useState } from "react";

import { api, ApiError, type AdminProductDetail } from "@/lib/api";

/** 원산지·제조사·포장 부피/무게 (상품정보 제공고시 위쪽에 보임) */
export function SpecSection({
  product,
  onSaved,
  show,
}: {
  product: AdminProductDetail;
  onSaved: (p: AdminProductDetail) => void;
  show: (kind: "ok" | "error", text: string) => void;
}) {
  const [origin, setOrigin] = useState(product.spec.origin ?? "");
  const [manufacturer, setManufacturer] = useState(product.spec.manufacturer ?? "");
  const [packageSize, setPackageSize] = useState(product.spec.packageSize ?? "");
  const [weightKg, setWeightKg] = useState(product.spec.packageWeightG == null ? "" : String(product.spec.packageWeightG / 1000));
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      const saved = await api.updateSpec(product.id, {
        origin: origin.trim() || null,
        manufacturer: manufacturer.trim() || null,
        packageSize: packageSize.trim() || null,
        packageWeightG: weightKg === "" ? null : Math.round(Number(weightKg) * 1000),
      });
      onSaved(saved);
      show("ok", "상품 정보를 저장했습니다.");
    } catch (e) {
      show("error", e instanceof ApiError ? (Object.values(e.fieldErrors)[0] ?? e.message) : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card flex flex-col gap-4 p-5">
      <h2 className="font-semibold">상품 정보</h2>
      <div className="grid gap-3 sm:grid-cols-4">
        <label className="flex flex-col gap-1">
          <span className="field-label">원산지</span>
          <input value={origin} onChange={(e) => setOrigin(e.target.value)} placeholder="예: 중국" className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">제조사·수입원</span>
          <input value={manufacturer} onChange={(e) => setManufacturer(e.target.value)} className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">포장 부피</span>
          <input value={packageSize} onChange={(e) => setPackageSize(e.target.value)} placeholder="예: 30x20x10cm" className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">포장 무게 (kg)</span>
          <input
            type="number"
            min={0}
            step={0.01}
            value={weightKg}
            onChange={(e) => setWeightKg(e.target.value)}
            className="input"
          />
        </label>
      </div>
      <button type="button" className="btn-primary self-end" disabled={saving} onClick={save}>
        상품 정보 저장
      </button>
    </section>
  );
}
