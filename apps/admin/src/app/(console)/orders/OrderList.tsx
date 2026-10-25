"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { Thumb } from "@/components/Thumb";
import { formatDate, formatWon, ITEM_STATUS_LABEL, orderAdminApi, type OrderSummary, type Page } from "@/lib/api";

// 처리해야 할 순서대로
const TABS = [
  { value: "PENDING_PAYMENT", label: "입금대기" },
  { value: "PAID", label: "신규 주문" },
  { value: "PREPARING", label: "상품준비중" },
  { value: "SHIPPED", label: "배송중" },
  { value: "CANCELLED", label: "취소" },
  { value: "", label: "전체" },
];

const STATUS_STYLE: Record<string, string> = {
  PENDING_PAYMENT: "bg-danger/10 text-danger",
  PAID: "bg-info/10 text-info",
  PREPARING: "bg-warn/10 text-warn",
  SHIPPED: "bg-ok/10 text-ok",
  CANCELLED: "bg-surface-muted text-ink-faint",
};

export function OrderList() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "PAID";
  const q = params.get("q") ?? "";
  const [data, setData] = useState<Page<OrderSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    orderAdminApi
      .list({ status, q })
      .then((result) => !cancelled && setData(result))
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [status, q]);

  function go(change: { status?: string; q?: string }) {
    const next = new URLSearchParams();
    const merged = { status, q, ...change };
    next.set("status", merged.status);
    if (merged.q) next.set("q", merged.q);
    router.push(`/orders?${next}`);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex gap-1 overflow-x-auto" aria-label="주문 상태">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => go({ status: tab.value })}
              aria-current={status === tab.value ? "page" : undefined}
              className={`rounded px-3 py-1.5 text-sm whitespace-nowrap ${
                status === tab.value ? "bg-ink font-semibold text-white" : "bg-surface text-ink-soft hover:bg-surface-muted"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            go({ q: String(new FormData(e.currentTarget).get("q") ?? "").trim() });
          }}
        >
          <input
            id="order-search"
            name="q"
            key={q}
            defaultValue={q}
            placeholder="주문번호 또는 주문자"
            aria-label="주문 검색"
            className="input w-52"
          />
          <button type="submit" className="btn-outline">
            검색
          </button>
        </form>
      </div>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full min-w-[48rem] text-sm">
          <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
            <tr>
              <th className="px-3 py-2.5 font-medium">주문</th>
              <th className="px-3 py-2.5 font-medium">상태</th>
              <th className="px-3 py-2.5 text-right font-medium">결제 금액</th>
              <th className="px-3 py-2.5 font-medium">주문 일시</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((order) => (
              <tr key={order.orderNo} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                <td className="px-3 py-2">
                  <Link href={`/orders/${order.orderNo}`} className="flex items-center gap-3">
                    <Thumb src={order.thumbnailUrl} className="size-11" />
                    <span className="flex flex-col">
                      <span className="font-medium hover:underline">{order.orderName}</span>
                      <span className="font-mono text-xs text-ink-faint">{order.orderNo}</span>
                    </span>
                  </Link>
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    {order.itemStatuses.map((s) => (
                      <span key={s} className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[s] ?? "bg-surface-muted"}`}>
                        {ITEM_STATUS_LABEL[s] ?? s}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{formatWon(order.payAmount)}</td>
                <td className="px-3 py-2 text-xs text-ink-faint tabular-nums">{formatDate(order.orderedAt)}</td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr>
                <td colSpan={4} className="px-3 py-12 text-center text-ink-faint">
                  해당하는 주문이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {data && <p className="text-xs text-ink-faint">총 {data.total.toLocaleString("ko-KR")}건</p>}
    </div>
  );
}
