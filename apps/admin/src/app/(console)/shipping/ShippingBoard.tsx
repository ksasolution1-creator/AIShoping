"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  ApiError,
  formatDate,
  formatWon,
  orderAdminApi,
  SHIPMENT_STATUS_LABEL,
  shippingApi,
  type Carrier,
  type ImportResult,
  type Page,
  type ReadyOrder,
  type ShipmentRow,
} from "@/lib/api";
import { SenderForm } from "./SenderForm";

const TABS = [
  { value: "new", label: "신규 주문", hint: "결제완료 → 발주확인" },
  { value: "ready", label: "발송 대기", hint: "송장 엑셀 → 송장번호 올리기" },
  { value: "transit", label: "배송중" },
  { value: "delivered", label: "배송완료" },
  { value: "sender", label: "보내는 사람·반품지" },
] as const;

type Tab = (typeof TABS)[number]["value"];

export function ShippingBoard() {
  const router = useRouter();
  const params = useSearchParams();
  const tab = (TABS.find((t) => t.value === params.get("tab"))?.value ?? "new") as Tab;

  return (
    <div className="flex flex-col gap-4">
      <nav className="flex gap-1 overflow-x-auto border-b border-line" aria-label="배송 단계">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => router.push(`/shipping?tab=${t.value}`)}
            aria-current={tab === t.value ? "page" : undefined}
            className={`-mb-px border-b-2 px-4 py-2 text-sm whitespace-nowrap ${
              tab === t.value ? "border-brand font-semibold text-ink" : "border-transparent text-ink-soft hover:text-ink"
            }`}
          >
            {t.label}
          </button>
        ))}
      </nav>
      {(tab === "new" || tab === "ready") && <OrderQueue key={tab} mode={tab} />}
      {(tab === "transit" || tab === "delivered") && <ShipmentList key={tab} mode={tab} />}
      {tab === "sender" && <SenderForm />}
    </div>
  );
}

/** 신규 주문(발주확인)·발송 대기(송장 처리) 목록 */
function OrderQueue({ mode }: { mode: "new" | "ready" }) {
  const [orders, setOrders] = useState<ReadyOrder[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  function load() {
    (mode === "new" ? shippingApi.newOrders() : shippingApi.ready())
      .then((list) => {
        setOrders(list);
        setSelected(new Set());
      })
      .catch((e: Error) => setMessage({ ok: false, text: e.message }));
  }

  useEffect(load, [mode]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setMessage(null);
    try {
      await action();
    } catch (e) {
      setMessage({ ok: false, text: e instanceof Error ? e.message : "처리하지 못했습니다." });
    } finally {
      setBusy(false);
    }
  }

  const chosen = [...selected];
  const all = orders ?? [];
  const allChecked = all.length > 0 && all.every((o) => selected.has(o.orderNo));

  return (
    <div className="flex flex-col gap-4">
      {mode === "ready" && <TrackingUpload onDone={load} />}

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-ink-soft">
          {mode === "new"
            ? "결제가 끝난 주문입니다. 확인하고 발주확인을 누르면 '발송 대기'로 넘어갑니다."
            : "선택한 주문을 송장 엑셀로 받아 택배사 프로그램에 올려 송장을 출력하세요."}
        </span>
        <div className="ml-auto flex gap-2">
          {mode === "new" && (
            <button
              type="button"
              className="btn-primary"
              disabled={busy || chosen.length === 0}
              onClick={() =>
                run(async () => {
                  const r = await shippingApi.accept(chosen);
                  setMessage({
                    ok: r.failed === 0,
                    text: `${r.success}건 발주확인했습니다.${r.failed ? ` ${r.failed}건 실패: ${r.errors.join(" / ")}` : ""}`,
                  });
                  load();
                })
              }
            >
              선택 발주확인 ({chosen.length})
            </button>
          )}
          <button
            type="button"
            className={mode === "ready" ? "btn-primary" : "btn-outline"}
            disabled={busy || chosen.length === 0}
            onClick={() => run(() => shippingApi.exportSheet(chosen))}
          >
            송장 엑셀 받기 ({chosen.length})
          </button>
        </div>
      </div>

      {message && (
        <p role="status" className={`rounded p-3 text-sm ${message.ok ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}>
          {message.text}
        </p>
      )}
      {!orders && <WhaleLoader />}

      {orders && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[48rem] text-sm">
            <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
              <tr>
                <th className="w-10 px-3 py-2.5">
                  <input
                    type="checkbox"
                    aria-label="모두 선택"
                    checked={allChecked}
                    onChange={() => setSelected(allChecked ? new Set() : new Set(all.map((o) => o.orderNo)))}
                  />
                </th>
                <th className="px-3 py-2.5 font-medium">주문번호</th>
                <th className="px-3 py-2.5 font-medium">받는 분</th>
                <th className="px-3 py-2.5 font-medium">상품</th>
                <th className="px-3 py-2.5 text-right font-medium">수량</th>
                <th className="px-3 py-2.5 text-right font-medium">결제 금액</th>
                <th className="px-3 py-2.5 font-medium">결제 일시</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.orderNo} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      aria-label={`${o.orderNo} 선택`}
                      checked={selected.has(o.orderNo)}
                      onChange={() =>
                        setSelected((prev) => {
                          const next = new Set(prev);
                          if (next.has(o.orderNo)) next.delete(o.orderNo);
                          else next.add(o.orderNo);
                          return next;
                        })
                      }
                    />
                  </td>
                  <td className="px-3 py-2">
                    <Link href={`/orders/${o.orderNo}`} className="font-mono text-xs hover:underline">
                      {o.orderNo}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{o.receiverName}</td>
                  <td className="px-3 py-2 text-ink-soft">{o.itemsText}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{o.totalQty}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{formatWon(o.payAmount)}</td>
                  <td className="px-3 py-2 text-xs text-ink-faint tabular-nums">{o.paidAt ? formatDate(o.paidAt) : "-"}</td>
                </tr>
              ))}
              {orders.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-3 py-12 text-center text-ink-faint">
                    {mode === "new" ? "새로 들어온 주문이 없습니다." : "발송을 기다리는 주문이 없습니다."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/** 송장번호가 채워진 엑셀을 올려 한 번에 출고 처리 */
function TrackingUpload({ onDone }: { onDone: () => void }) {
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [carrierId, setCarrierId] = useState<number | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    orderAdminApi
      .carriers()
      .then((list) => {
        setCarriers(list);
        setCarrierId(list[0]?.id ?? null);
      })
      .catch(() => setCarriers([]));
  }, []);

  async function upload(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const file = fileRef.current?.files?.[0];
    if (!file) {
      setError("송장번호가 들어 있는 엑셀 파일을 골라 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const r = await shippingApi.importTracking(file, carrierId);
      setResult(r);
      if (fileRef.current) fileRef.current.value = "";
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "올리지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card flex flex-col gap-3 p-4">
      <h2 className="font-semibold">송장번호 일괄 등록</h2>
      <p className="text-xs text-ink-soft">
        &lsquo;주문번호&rsquo;와 &lsquo;운송장번호&rsquo; 열이 있는 엑셀(xlsx·xls)이나 CSV를 올리면 한 번에 출고 처리되고 재고가 빠집니다.
        택배사 열이 없으면 아래 기본 택배사로 등록합니다. 위에서 받은 송장 엑셀의 마지막 열에 송장번호를 채워 올려도 됩니다.
      </p>
      <form onSubmit={upload} className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1">
          <span className="field-label">기본 택배사</span>
          <select value={carrierId ?? ""} onChange={(e) => setCarrierId(Number(e.target.value) || null)} className="input w-40">
            {carriers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-1 flex-col gap-1">
          <span className="field-label">엑셀 파일</span>
          <input ref={fileRef} type="file" accept=".xlsx,.xls,.csv" className="input py-1.5" />
        </label>
        <button type="submit" className="btn-primary" disabled={busy}>
          {busy ? "처리 중…" : "올리고 출고 처리"}
        </button>
      </form>
      {error && <p className="text-sm text-danger">{error}</p>}
      {result && (
        <div className="flex flex-col gap-2">
          <p className={`text-sm font-medium ${result.failed ? "text-warn" : "text-ok"}`}>
            {result.success}건 출고 완료{result.failed > 0 && `, ${result.failed}건 실패`}
          </p>
          {result.failed > 0 && (
            <ul className="max-h-48 overflow-y-auto rounded border border-line text-xs">
              {result.lines
                .filter((l) => !l.ok)
                .map((l) => (
                  <li key={l.row} className="flex gap-3 border-b border-line px-3 py-1.5 last:border-0">
                    <span className="w-14 text-ink-faint">{l.row}번째 줄</span>
                    <span className="w-44 font-mono">{l.orderNo || "-"}</span>
                    <span className="text-danger">{l.message}</span>
                  </li>
                ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}

function ShipmentList({ mode }: { mode: "transit" | "delivered" }) {
  const [data, setData] = useState<Page<ShipmentRow> | null>(null);
  const [q, setQ] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    shippingApi
      .shipments({ status: mode === "transit" ? "IN_TRANSIT" : "DELIVERED", q })
      .then(setData)
      .catch((e: Error) => setError(e.message));
  }, [mode, q, reload]);

  async function delivered(id: number) {
    try {
      await shippingApi.delivered(id);
      setReload((n) => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "처리하지 못했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setQ(String(new FormData(e.currentTarget).get("q") ?? "").trim());
        }}
      >
        <input name="q" placeholder="주문번호 · 송장번호 · 받는 분" aria-label="배송 검색" className="input w-64" />
        <button type="submit" className="btn-outline">
          검색
        </button>
      </form>
      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}
      {!data && !error && <WhaleLoader />}
      {data && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[52rem] text-sm">
            <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
              <tr>
                <th className="px-3 py-2.5 font-medium">주문번호</th>
                <th className="px-3 py-2.5 font-medium">받는 분</th>
                <th className="px-3 py-2.5 font-medium">상품</th>
                <th className="px-3 py-2.5 font-medium">택배사 / 송장번호</th>
                <th className="px-3 py-2.5 font-medium">{mode === "transit" ? "출고일" : "배송완료일"}</th>
                <th className="px-3 py-2.5 font-medium">상태</th>
                {mode === "transit" && <th className="px-3 py-2.5" />}
              </tr>
            </thead>
            <tbody>
              {data.items.map((s) => (
                <tr key={s.id} className="border-b border-line last:border-0">
                  <td className="px-3 py-2">
                    <Link href={`/orders/${s.orderNo}`} className="font-mono text-xs hover:underline">
                      {s.orderNo}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{s.receiverName}</td>
                  <td className="px-3 py-2 text-ink-soft">{s.itemsText}</td>
                  <td className="px-3 py-2">
                    {s.carrierName}{" "}
                    {s.trackingUrl ? (
                      <a href={s.trackingUrl} target="_blank" rel="noreferrer" className="font-mono text-xs text-brand underline">
                        {s.trackingNo}
                      </a>
                    ) : (
                      <span className="font-mono text-xs">{s.trackingNo}</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-xs text-ink-faint tabular-nums">
                    {formatDateOrDash(mode === "transit" ? s.shippedAt : s.deliveredAt)}
                  </td>
                  <td className="px-3 py-2 text-xs">{SHIPMENT_STATUS_LABEL[s.status] ?? s.status}</td>
                  {mode === "transit" && (
                    <td className="px-3 py-2 text-right">
                      <button type="button" className="btn-outline h-8" onClick={() => delivered(s.id)}>
                        배송완료 처리
                      </button>
                    </td>
                  )}
                </tr>
              ))}
              {data.items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-3 py-12 text-center text-ink-faint">
                    {mode === "transit" ? "배송 중인 송장이 없습니다." : "배송완료된 송장이 없습니다."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      {data && <p className="text-xs text-ink-faint">총 {data.total.toLocaleString("ko-KR")}건</p>}
    </div>
  );
}

function formatDateOrDash(iso: string | null) {
  return iso ? formatDate(iso) : "-";
}
