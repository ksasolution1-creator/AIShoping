"use client";

import { useState } from "react";

import { api, ApiError, formatWon, type ListSku } from "@/lib/api";

/** 상품 목록의 판매가. 누르면 그 자리에서 입력하고 Enter(또는 저장)로 바로 저장, Esc로 취소 */
export function PriceQuickEdit({
  sku,
  onSaved,
}: {
  sku: ListSku;
  onSaved: (price: number) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function start() {
    setValue(String(sku.price));
    setError(null);
    setEditing(true);
  }

  async function save() {
    const price = Number(value.replaceAll(",", "").trim());
    if (!value.trim() || !Number.isInteger(price) || price < 0) {
      setError("0 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (price === sku.price) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api.changeSkuPrice(sku.skuId, price);
      onSaved(price);
      setEditing(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  if (!editing) {
    return (
      <button
        type="button"
        onClick={start}
        title="눌러서 판매가 변경"
        className="rounded border border-transparent px-1.5 py-0.5 text-sm font-medium tabular-nums whitespace-nowrap hover:border-brand/60 hover:bg-surface-muted"
      >
        {formatWon(sku.price)}
      </button>
    );
  }

  return (
    <span className="inline-flex flex-col items-end gap-0.5">
      <span className="inline-flex items-center gap-1">
        <input
          autoFocus
          aria-label="판매가"
          inputMode="numeric"
          value={value}
          disabled={saving}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") void save();
            if (e.key === "Escape") setEditing(false);
          }}
          className="input h-7 w-24 px-2 text-right text-sm tabular-nums"
        />
        <button
          type="button"
          className="btn-primary h-7 px-2 text-xs"
          disabled={saving}
          onClick={save}
        >
          저장
        </button>
        <button
          type="button"
          className="btn-outline h-7 px-2 text-xs"
          disabled={saving}
          onClick={() => setEditing(false)}
        >
          취소
        </button>
      </span>
      {error && <span className="text-xs text-danger">{error}</span>}
    </span>
  );
}
