"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import {
  ApiError,
  formatWon,
  inventoryApi,
  type Location,
  type PurchaseDetail,
  type StockRow,
  type Supplier,
} from "@/lib/api";

type Line = {
  skuId: number;
  skuCode: string;
  productName: string;
  optionText: string;
  gradeLabel: string;
  qty: number;
  unitCost: number;
};

const today = () => new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });

/**
 * 매입서 작성·수정. 바코드를 찍거나 상품명으로 찾아 품목을 넣고,
 * 저장만 하면 '작성 중', 입고 확정을 누르면 재고가 늘어난다.
 */
export function PurchaseEditor({ initial }: { initial: PurchaseDetail | null }) {
  const router = useRouter();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [supplierId, setSupplierId] = useState<number | null>(initial?.supplierId ?? null);
  const [locationId, setLocationId] = useState<number | null>(initial?.locationId ?? null);
  const [purchasedOn, setPurchasedOn] = useState(initial?.purchasedOn ?? today());
  const [memo, setMemo] = useState(initial?.memo ?? "");
  const [lines, setLines] = useState<Line[]>(
    initial?.items.map((i) => ({
      skuId: i.skuId,
      skuCode: i.skuCode,
      productName: i.productName,
      optionText: i.optionText,
      gradeLabel: i.grade.label,
      qty: i.qty,
      unitCost: i.unitCost,
    })) ?? [],
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    inventoryApi.suppliers().then(setSuppliers).catch(() => setSuppliers([]));
    inventoryApi
      .locations()
      .then((list) => {
        setLocations(list);
        setLocationId((current) => current ?? list.find((l) => l.type === "WAREHOUSE")?.id ?? list[0]?.id ?? null);
      })
      .catch(() => setLocations([]));
  }, []);

  function addSku(row: StockRow) {
    setLines((prev) => {
      const found = prev.find((l) => l.skuId === row.skuId);
      // 같은 바코드를 또 찍으면 수량을 하나 늘린다
      if (found) return prev.map((l) => (l.skuId === row.skuId ? { ...l, qty: l.qty + 1 } : l));
      return [
        ...prev,
        {
          skuId: row.skuId,
          skuCode: row.skuCode,
          productName: row.productName,
          optionText: row.optionText,
          gradeLabel: row.grade.label,
          qty: 1,
          unitCost: row.cost ?? 0,
        },
      ];
    });
  }

  function patch(skuId: number, change: Partial<Line>) {
    setLines((prev) => prev.map((l) => (l.skuId === skuId ? { ...l, ...change } : l)));
  }

  async function save(receive: boolean) {
    if (!locationId) {
      setError("입고 위치를 선택해 주세요.");
      return;
    }
    if (lines.length === 0) {
      setError("매입 상품을 한 개 이상 넣어 주세요.");
      return;
    }
    if (receive && !window.confirm(`${totalQty.toLocaleString("ko-KR")}개를 입고합니다. 입고 후에는 수정할 수 없습니다.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const body = {
        supplierId,
        locationId,
        purchasedOn,
        memo: memo.trim() || null,
        items: lines.map((l) => ({ skuId: l.skuId, qty: l.qty, unitCost: l.unitCost })),
      };
      const saved = initial ? await inventoryApi.updatePurchase(initial.id, body) : await inventoryApi.createPurchase(body);
      if (receive) await inventoryApi.receivePurchase(saved.id);
      router.replace(`/purchases/${saved.id}${receive ? "?received=1" : ""}`);
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "저장하지 못했습니다.");
      setBusy(false);
    }
  }

  async function cancelPurchase() {
    if (!initial || !window.confirm("이 매입서를 취소할까요? 재고는 바뀌지 않습니다.")) return;
    setBusy(true);
    try {
      await inventoryApi.cancelPurchase(initial.id);
      router.replace("/purchases");
    } catch (err) {
      setError(err instanceof Error ? err.message : "취소하지 못했습니다.");
      setBusy(false);
    }
  }

  const totalQty = lines.reduce((sum, l) => sum + l.qty, 0);
  const totalCost = lines.reduce((sum, l) => sum + l.qty * l.unitCost, 0);

  return (
    <div className="flex flex-col gap-4">
      <section className="card grid gap-3 p-4 sm:grid-cols-4">
        <label className="flex flex-col gap-1">
          <span className="field-label">매입처</span>
          <SupplierPicker
            suppliers={suppliers}
            value={supplierId}
            onChange={setSupplierId}
            onCreated={(s) => setSuppliers((prev) => [...prev, s])}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">입고 위치</span>
          <select value={locationId ?? ""} onChange={(e) => setLocationId(Number(e.target.value))} className="input">
            {locations.map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">매입일</span>
          <input type="date" value={purchasedOn} onChange={(e) => setPurchasedOn(e.target.value)} className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">메모</span>
          <input value={memo} onChange={(e) => setMemo(e.target.value)} maxLength={500} placeholder="예: 10월 반품 땡처리분" className="input" />
        </label>
      </section>

      <section className="card flex flex-col gap-3 p-4">
        <SkuSearch onPick={addSku} />
        <div className="overflow-x-auto">
          <table className="w-full min-w-[44rem] text-sm">
            <thead className="border-b border-line text-left text-xs text-ink-soft">
              <tr>
                <th className="py-2 font-medium">상품</th>
                <th className="w-28 py-2 text-right font-medium">수량</th>
                <th className="w-36 py-2 text-right font-medium">매입단가</th>
                <th className="w-32 py-2 text-right font-medium">금액</th>
                <th className="w-12" />
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.skuId} className="border-b border-line last:border-0">
                  <td className="py-2">
                    <div className="font-medium">{l.productName}</div>
                    <div className="text-xs text-ink-soft">
                      {l.gradeLabel}
                      {l.optionText && ` · ${l.optionText}`} · <span className="font-mono">{l.skuCode}</span>
                    </div>
                  </td>
                  <td className="py-2 text-right">
                    <input
                      type="number"
                      min={1}
                      value={l.qty}
                      aria-label={`${l.productName} 수량`}
                      onChange={(e) => patch(l.skuId, { qty: Math.max(1, Number(e.target.value) || 1) })}
                      className="input w-24 text-right"
                    />
                  </td>
                  <td className="py-2 text-right">
                    <input
                      type="number"
                      min={0}
                      step={10}
                      value={l.unitCost}
                      aria-label={`${l.productName} 매입단가`}
                      onChange={(e) => patch(l.skuId, { unitCost: Math.max(0, Number(e.target.value) || 0) })}
                      className="input w-32 text-right"
                    />
                  </td>
                  <td className="py-2 text-right tabular-nums">{formatWon(l.qty * l.unitCost)}</td>
                  <td className="py-2 text-right">
                    <button
                      type="button"
                      onClick={() => setLines((prev) => prev.filter((x) => x.skuId !== l.skuId))}
                      aria-label={`${l.productName} 빼기`}
                      className="rounded px-2 py-1 text-ink-faint hover:bg-surface-muted hover:text-danger"
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              ))}
              {lines.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-ink-faint">
                    위 검색칸에 바코드를 찍거나 상품명을 넣어 매입 상품을 추가하세요.
                  </td>
                </tr>
              )}
            </tbody>
            {lines.length > 0 && (
              <tfoot className="border-t-2 border-line font-semibold">
                <tr>
                  <td className="py-2">합계 {lines.length}종</td>
                  <td className="py-2 text-right tabular-nums">{totalQty.toLocaleString("ko-KR")}개</td>
                  <td />
                  <td className="py-2 text-right tabular-nums">{formatWon(totalCost)}</td>
                  <td />
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </section>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}

      <div className="flex flex-wrap justify-end gap-2">
        {initial && (
          <button type="button" className="btn-outline mr-auto text-danger" disabled={busy} onClick={cancelPurchase}>
            매입서 취소
          </button>
        )}
        <button type="button" className="btn-outline" disabled={busy} onClick={() => save(false)}>
          저장 (입고 전)
        </button>
        <button type="button" className="btn-primary" disabled={busy} onClick={() => save(true)}>
          저장하고 입고 확정
        </button>
      </div>
    </div>
  );
}

/** 바코드 스캔 또는 상품명 검색. 결과가 하나면 바로 넣는다 */
function SkuSearch({ onPick }: { onPick: (row: StockRow) => void }) {
  const [results, setResults] = useState<StockRow[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function search(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const q = inputRef.current?.value.trim() ?? "";
    if (!q) return;
    setMessage(null);
    try {
      const page = await inventoryApi.stock({ q, size: 20 });
      if (page.items.length === 1) {
        onPick(page.items[0]);
        setResults(null);
        inputRef.current!.value = "";
      } else if (page.items.length === 0) {
        setResults(null);
        setMessage(`'${q}'에 맞는 상품이 없습니다. 새 상품이면 상품 관리에서 먼저 등록해 주세요.`);
      } else {
        setResults(page.items);
      }
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "찾지 못했습니다.");
    }
    inputRef.current?.focus();
  }

  return (
    <div className="flex flex-col gap-2">
      <form onSubmit={search} className="flex gap-2">
        <input ref={inputRef} autoFocus placeholder="바코드 스캔 · SKU코드 · 상품명으로 추가" aria-label="매입 상품 찾기" className="input flex-1" />
        <button type="submit" className="btn-outline">
          추가
        </button>
      </form>
      {message && <p className="text-sm text-warn">{message}</p>}
      {results && (
        <ul className="max-h-64 divide-y divide-line overflow-y-auto rounded border border-line">
          {results.map((r) => (
            <li key={r.skuId}>
              <button
                type="button"
                onClick={() => {
                  onPick(r);
                  setResults(null);
                  if (inputRef.current) inputRef.current.value = "";
                  inputRef.current?.focus();
                }}
                className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-surface-muted"
              >
                <span>
                  {r.productName}
                  <span className="ml-2 text-xs text-ink-soft">
                    {r.grade.label}
                    {r.optionText && ` · ${r.optionText}`}
                  </span>
                </span>
                <span className="font-mono text-xs text-ink-faint">{r.skuCode}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function SupplierPicker({
  suppliers,
  value,
  onChange,
  onCreated,
}: {
  suppliers: Supplier[];
  value: number | null;
  onChange: (id: number | null) => void;
  onCreated: (s: Supplier) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");

  async function add() {
    if (!name.trim()) return;
    const { id } = await inventoryApi.createSupplier({
      name: name.trim(),
      businessNo: null,
      contactName: null,
      phone: null,
      memo: null,
    });
    onCreated({ id, name: name.trim(), businessNo: null, contactName: null, phone: null, memo: null, active: true });
    onChange(id);
    setAdding(false);
    setName("");
  }

  if (adding) {
    return (
      <span className="flex gap-1">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              void add();
            }
          }}
          autoFocus
          placeholder="새 매입처 이름"
          className="input min-w-0 flex-1"
        />
        <button type="button" className="btn-outline px-2" onClick={() => void add()}>
          추가
        </button>
      </span>
    );
  }
  return (
    <select
      value={value ?? ""}
      onChange={(e) => {
        if (e.target.value === "__new") setAdding(true);
        else onChange(e.target.value ? Number(e.target.value) : null);
      }}
      className="input"
    >
      <option value="">선택 안 함</option>
      {suppliers.map((s) => (
        <option key={s.id} value={s.id}>
          {s.name}
        </option>
      ))}
      <option value="__new">+ 새 매입처 추가</option>
    </select>
  );
}
