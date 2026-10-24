"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  ApiError,
  formatDate,
  formatWon,
  inventoryApi,
  MOVEMENT_LABEL,
  type Location,
  type Movement,
  type Page,
  type StockRow,
} from "@/lib/api";
import { openLabelPrint } from "@/lib/labels";

const LOW_STOCK = 5;

type Panel = { skuId: number; mode: "adjust" | "transfer" | "history" };

export function StockBoard() {
  const router = useRouter();
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const locationId = params.get("location") ? Number(params.get("location")) : null;
  const low = params.get("low") === "1";
  const page = Number(params.get("page") ?? 0);

  const [data, setData] = useState<Page<StockRow> | null>(null);
  const [locations, setLocations] = useState<Location[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [panel, setPanel] = useState<Panel | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inventoryApi.locations().then(setLocations).catch(() => setLocations([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    inventoryApi
      .stock({ q, locationId, maxAvailable: low ? LOW_STOCK : null, page })
      .then((result) => {
        if (cancelled) return;
        setData(result);
        setError(null);
        // 바코드를 찍어 한 개만 나오면 바로 조정 칸을 연다
        if (q && result.items.length === 1) setPanel({ skuId: result.items[0].skuId, mode: "adjust" });
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [q, locationId, low, page]);

  function go(change: { q?: string; location?: number | null; low?: boolean; page?: number }) {
    const merged = { q, location: locationId, low, page: 0, ...change };
    const next = new URLSearchParams();
    if (merged.q) next.set("q", merged.q);
    if (merged.location) next.set("location", String(merged.location));
    if (merged.low) next.set("low", "1");
    if (merged.page) next.set("page", String(merged.page));
    setPanel(null);
    router.push(`/inventory?${next}`);
  }

  function toggle(skuId: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(skuId)) next.delete(skuId);
      else next.add(skuId);
      return next;
    });
  }

  function printLabels() {
    const rows = data?.items.filter((r) => selected.has(r.skuId)) ?? [];
    openLabelPrint(
      rows.map((r) => ({
        skuCode: r.skuCode,
        productName: r.productName,
        optionText: r.optionText,
        gradeLabel: r.grade.label,
        price: r.price,
        qty: Math.max(r.onHand, 1),
      })),
    );
  }

  function updated(row: StockRow) {
    setData((prev) => prev && { ...prev, items: prev.items.map((r) => (r.skuId === row.skuId ? row : r)) });
  }

  const rows = data?.items ?? [];
  const allChecked = rows.length > 0 && rows.every((r) => selected.has(r.skuId));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            go({ q: String(new FormData(e.currentTarget).get("q") ?? "").trim() });
            searchRef.current?.select();
          }}
        >
          <input
            ref={searchRef}
            name="q"
            key={q}
            defaultValue={q}
            autoFocus
            placeholder="바코드 스캔 · SKU코드 · 상품명"
            aria-label="재고 검색"
            className="input w-72"
          />
          <button type="submit" className="btn-outline">
            검색
          </button>
        </form>
        <select
          aria-label="재고 위치"
          value={locationId ?? ""}
          onChange={(e) => go({ location: e.target.value ? Number(e.target.value) : null })}
          className="input w-40"
        >
          <option value="">전체 위치</option>
          {locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
        <label className="flex h-9 items-center gap-2 text-sm">
          <input type="checkbox" checked={low} onChange={(e) => go({ low: e.target.checked })} />
          품절 임박 ({LOW_STOCK}개 이하)
        </label>
        <div className="ml-auto flex gap-2">
          <button type="button" className="btn-outline" disabled={selected.size === 0} onClick={printLabels}>
            바코드 라벨 인쇄{selected.size > 0 && ` (${selected.size})`}
          </button>
          <Link href="/purchases/new" className="btn-primary">
            매입 등록
          </Link>
        </div>
      </div>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}
      {!data && !error && <WhaleLoader />}

      {data && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[60rem] text-sm">
            <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
              <tr>
                <th className="w-10 px-3 py-2.5">
                  <input
                    type="checkbox"
                    aria-label="이 페이지 모두 선택"
                    checked={allChecked}
                    onChange={() =>
                      setSelected((prev) => {
                        const next = new Set(prev);
                        rows.forEach((r) => (allChecked ? next.delete(r.skuId) : next.add(r.skuId)));
                        return next;
                      })
                    }
                  />
                </th>
                <th className="px-3 py-2.5 font-medium">상품 / SKU</th>
                <th className="px-3 py-2.5 text-right font-medium">판매가 / 매입가</th>
                <th className="px-3 py-2.5 font-medium">위치별 보유</th>
                <th className="px-3 py-2.5 text-right font-medium">보유</th>
                <th className="px-3 py-2.5 text-right font-medium">주문예약</th>
                <th className="px-3 py-2.5 text-right font-medium">판매가능</th>
                <th className="px-3 py-2.5 font-medium" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const available = row.onHand - row.reserved;
                const open = panel?.skuId === row.skuId ? panel : null;
                return (
                  <StockTableRow
                    key={row.skuId}
                    row={row}
                    available={available}
                    checked={selected.has(row.skuId)}
                    onCheck={() => toggle(row.skuId)}
                    panel={open}
                    onPanel={(mode) => setPanel(open?.mode === mode ? null : { skuId: row.skuId, mode })}
                    locations={locations}
                    onUpdated={updated}
                  />
                );
              })}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-3 py-12 text-center text-ink-faint">
                    {q ? `'${q}'에 맞는 재고가 없습니다.` : "등록된 상품이 없습니다."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && (
        <div className="flex items-center justify-between text-xs text-ink-faint">
          <span>총 {data.total.toLocaleString("ko-KR")}개 SKU</span>
          {data.total > data.size && (
            <span className="flex gap-2">
              <button type="button" className="btn-outline h-7" disabled={page === 0} onClick={() => go({ page: page - 1 })}>
                이전
              </button>
              <button
                type="button"
                className="btn-outline h-7"
                disabled={(page + 1) * data.size >= data.total}
                onClick={() => go({ page: page + 1 })}
              >
                다음
              </button>
            </span>
          )}
        </div>
      )}
    </div>
  );
}

function StockTableRow({
  row,
  available,
  checked,
  onCheck,
  panel,
  onPanel,
  locations,
  onUpdated,
}: {
  row: StockRow;
  available: number;
  checked: boolean;
  onCheck: () => void;
  panel: Panel | null;
  onPanel: (mode: Panel["mode"]) => void;
  locations: Location[];
  onUpdated: (row: StockRow) => void;
}) {
  return (
    <>
      <tr className="border-b border-line last:border-0 hover:bg-surface-muted/60">
        <td className="px-3 py-2">
          <input type="checkbox" aria-label={`${row.productName} 선택`} checked={checked} onChange={onCheck} />
        </td>
        <td className="px-3 py-2">
          <Link href={`/products/${row.productId}`} className="font-medium hover:underline">
            {row.productName}
          </Link>
          <div className="flex flex-wrap items-center gap-x-2 text-xs text-ink-soft">
            <span className="rounded bg-surface-muted px-1.5 py-0.5">{row.grade.label}</span>
            {row.optionText && <span>{row.optionText}</span>}
            <span className="font-mono text-ink-faint">{row.skuCode}</span>
            {row.barcode && <span className="font-mono text-ink-faint">· {row.barcode}</span>}
          </div>
        </td>
        <td className="px-3 py-2 text-right tabular-nums">
          {formatWon(row.price)}
          <div className="text-xs text-ink-faint">{row.cost == null ? "매입가 없음" : formatWon(row.cost)}</div>
        </td>
        <td className="px-3 py-2 text-xs text-ink-soft">
          {row.locations.length === 0
            ? "-"
            : row.locations.map((l) => (
                <div key={l.locationId}>
                  {l.locationName} <span className="tabular-nums">{l.onHand}</span>
                </div>
              ))}
        </td>
        <td className="px-3 py-2 text-right tabular-nums">{row.onHand}</td>
        <td className="px-3 py-2 text-right text-ink-soft tabular-nums">{row.reserved}</td>
        <td
          className={`px-3 py-2 text-right font-semibold tabular-nums ${
            available <= 0 ? "text-danger" : available <= LOW_STOCK ? "text-warn" : ""
          }`}
        >
          {available}
        </td>
        <td className="px-3 py-2">
          <div className="flex justify-end gap-1">
            {(["adjust", "transfer", "history"] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => onPanel(mode)}
                aria-expanded={panel?.mode === mode}
                className={`rounded px-2 py-1 text-xs ${
                  panel?.mode === mode ? "bg-ink text-white" : "bg-surface-muted text-ink-soft hover:bg-line"
                }`}
              >
                {{ adjust: "조정", transfer: "이동", history: "이력" }[mode]}
              </button>
            ))}
          </div>
        </td>
      </tr>
      {panel && (
        <tr className="border-b border-line bg-surface-muted/50">
          <td colSpan={8} className="px-4 py-3">
            {panel.mode === "adjust" && <AdjustForm row={row} locations={locations} onDone={onUpdated} />}
            {panel.mode === "transfer" && <TransferForm row={row} locations={locations} onDone={onUpdated} />}
            {panel.mode === "history" && <MovementList skuId={row.skuId} />}
          </td>
        </tr>
      )}
    </>
  );
}

function AdjustForm({ row, locations, onDone }: { row: StockRow; locations: Location[]; onDone: (r: StockRow) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const sign = data.get("direction") === "out" ? -1 : 1;
    setBusy(true);
    setError(null);
    try {
      const updated = await inventoryApi.adjust({
        skuId: row.skuId,
        locationId: Number(data.get("locationId")),
        qty: sign * Number(data.get("qty")),
        memo: String(data.get("memo")),
      });
      form.reset();
      onDone(updated);
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "조정하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
      <LocationSelect name="locationId" label="위치" locations={locations} />
      <label className="flex flex-col gap-1">
        <span className="field-label">구분</span>
        <select name="direction" className="input w-24">
          <option value="in">늘리기 +</option>
          <option value="out">줄이기 −</option>
        </select>
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">수량</span>
        <input name="qty" type="number" min={1} required className="input w-24" autoFocus />
      </label>
      <label className="flex flex-1 flex-col gap-1">
        <span className="field-label">사유</span>
        <input name="memo" required maxLength={200} placeholder="예: 실사 차이, 파손, 분실" className="input min-w-48" />
      </label>
      <button type="submit" className="btn-primary" disabled={busy}>
        조정
      </button>
      {error && <p className="basis-full text-sm text-danger">{error}</p>}
    </form>
  );
}

function TransferForm({ row, locations, onDone }: { row: StockRow; locations: Location[]; onDone: (r: StockRow) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError(null);
    try {
      const updated = await inventoryApi.transfer({
        skuId: row.skuId,
        fromLocationId: Number(data.get("from")),
        toLocationId: Number(data.get("to")),
        qty: Number(data.get("qty")),
        memo: String(data.get("memo") ?? "") || null,
      });
      form.reset();
      onDone(updated);
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "옮기지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (locations.length < 2) {
    return <p className="text-sm text-ink-soft">재고 위치가 두 곳 이상이어야 옮길 수 있습니다.</p>;
  }
  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
      <LocationSelect name="from" label="출발" locations={locations} />
      <LocationSelect name="to" label="도착" locations={locations} defaultIndex={1} />
      <label className="flex flex-col gap-1">
        <span className="field-label">수량</span>
        <input name="qty" type="number" min={1} required className="input w-24" autoFocus />
      </label>
      <label className="flex flex-1 flex-col gap-1">
        <span className="field-label">메모</span>
        <input name="memo" maxLength={200} placeholder="예: 매장 진열" className="input min-w-48" />
      </label>
      <button type="submit" className="btn-primary" disabled={busy}>
        이동
      </button>
      {error && <p className="basis-full text-sm text-danger">{error}</p>}
    </form>
  );
}

function LocationSelect({
  name,
  label,
  locations,
  defaultIndex = 0,
}: {
  name: string;
  label: string;
  locations: Location[];
  defaultIndex?: number;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="field-label">{label}</span>
      <select name={name} defaultValue={locations[defaultIndex]?.id} className="input w-36">
        {locations.map((l) => (
          <option key={l.id} value={l.id}>
            {l.name}
          </option>
        ))}
      </select>
    </label>
  );
}

function MovementList({ skuId }: { skuId: number }) {
  const [items, setItems] = useState<Movement[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    inventoryApi
      .movements(skuId)
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, [skuId]);

  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (!items) return <WhaleLoader size="sm" />;
  if (items.length === 0) return <p className="text-sm text-ink-faint">재고 변동 기록이 없습니다.</p>;
  return (
    <table className="w-full text-xs">
      <thead className="text-left text-ink-faint">
        <tr>
          <th className="py-1 font-medium">일시</th>
          <th className="py-1 font-medium">구분</th>
          <th className="py-1 font-medium">위치</th>
          <th className="py-1 text-right font-medium">수량</th>
          <th className="py-1 pl-4 font-medium">메모</th>
          <th className="py-1 font-medium">처리자</th>
        </tr>
      </thead>
      <tbody>
        {items.map((m) => (
          <tr key={m.id} className="border-t border-line">
            <td className="py-1 tabular-nums">{formatDate(m.createdAt)}</td>
            <td className="py-1">{MOVEMENT_LABEL[m.type] ?? m.type}</td>
            <td className="py-1">{m.locationName}</td>
            <td className={`py-1 text-right font-semibold tabular-nums ${m.qty > 0 ? "text-ok" : "text-danger"}`}>
              {m.qty > 0 ? `+${m.qty}` : m.qty}
            </td>
            <td className="py-1 pl-4 text-ink-soft">{m.memo}</td>
            <td className="py-1 text-ink-soft">{m.actorName ?? "-"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
