"use client";

import { useState } from "react";

import { api, ApiError, type ListSku } from "@/lib/api";

/**
 * 상품 목록의 재고. 누르면 지금 몇 개인지 입력한다 (예: 5 → 3).
 * 차이만큼 서버가 입고·차감하고 조정 기록을 남긴다. Enter로 저장, Esc로 취소
 */
export function StockQuickEdit({
  sku,
  onSaved,
}: {
  sku: ListSku;
  onSaved: (stock: { onHand: number; reserved: number }) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const available = sku.onHand - sku.reserved;

  function start() {
    setValue(String(sku.onHand));
    setError(null);
    setEditing(true);
  }

  async function save() {
    const qty = Number(value.trim());
    if (!value.trim() || !Number.isInteger(qty) || qty < 0) {
      setError("0 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (qty < sku.reserved) {
      setError(`주문이 잡아 둔 ${sku.reserved}개보다 적게 할 수 없습니다.`);
      return;
    }
    if (qty === sku.onHand) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.setStock(sku.skuId, qty));
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
        title="눌러서 재고 수량 변경"
        className="rounded border border-transparent px-1.5 py-0.5 text-right text-sm tabular-nums whitespace-nowrap hover:border-brand/60 hover:bg-surface-muted"
      >
        <span className={available <= 0 ? "font-semibold text-danger" : "font-medium"}>{sku.onHand}개</span>
        {sku.reserved > 0 && <span className="text-xs text-ink-faint"> (예약 {sku.reserved})</span>}
      </button>
    );
  }

  return (
    <span className="inline-flex flex-col items-end gap-0.5">
      <span className="inline-flex items-center gap-1">
        <input
          autoFocus
          aria-label="재고 수량"
          type="number"
          min={sku.reserved}
          value={value}
          disabled={saving}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") void save();
            if (e.key === "Escape") setEditing(false);
          }}
          className="input h-7 w-16 px-2 text-right text-sm tabular-nums"
        />
        <button type="button" className="btn-primary h-7 px-2 text-xs" disabled={saving} onClick={save}>
          저장
        </button>
        <button type="button" className="btn-outline h-7 px-2 text-xs" disabled={saving} onClick={() => setEditing(false)}>
          취소
        </button>
      </span>
      {error && <span className="text-xs text-danger">{error}</span>}
    </span>
  );
}
