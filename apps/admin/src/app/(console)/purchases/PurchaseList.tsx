"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  formatWon,
  inventoryApi,
  PURCHASE_STATUS_LABEL,
  type Page,
  type PurchaseStatus,
  type PurchaseSummary,
} from "@/lib/api";

const TABS: { value: "" | PurchaseStatus; label: string }[] = [
  { value: "", label: "전체" },
  { value: "DRAFT", label: "작성 중" },
  { value: "RECEIVED", label: "입고 완료" },
  { value: "CANCELLED", label: "취소" },
];

export const PURCHASE_STATUS_STYLE: Record<PurchaseStatus, string> = {
  DRAFT: "bg-warn/10 text-warn",
  RECEIVED: "bg-ok/10 text-ok",
  CANCELLED: "bg-surface-muted text-ink-faint",
};

export function PurchaseList() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "";
  const q = params.get("q") ?? "";
  const [data, setData] = useState<Page<PurchaseSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    inventoryApi
      .purchases({ status, q })
      .then((result) => !cancelled && setData(result))
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [status, q]);

  function go(change: { status?: string; q?: string }) {
    const merged = { status, q, ...change };
    const next = new URLSearchParams();
    if (merged.status) next.set("status", merged.status);
    if (merged.q) next.set("q", merged.q);
    router.push(`/purchases?${next}`);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex gap-1" aria-label="매입 상태">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => go({ status: tab.value })}
              aria-current={status === tab.value ? "page" : undefined}
              className={`rounded px-3 py-1.5 text-sm ${
                status === tab.value ? "bg-ink font-semibold text-white" : "bg-surface text-ink-soft hover:bg-surface-muted"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
        <div className="flex gap-2">
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              go({ q: String(new FormData(e.currentTarget).get("q") ?? "").trim() });
            }}
          >
            <input name="q" key={q} defaultValue={q} placeholder="매입번호 · 매입처" aria-label="매입 검색" className="input w-48" />
            <button type="submit" className="btn-outline">
              검색
            </button>
          </form>
          <Link href="/purchases/new" className="btn-primary">
            매입 등록
          </Link>
        </div>
      </div>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}
      {!data && !error && <WhaleLoader />}

      {data && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[48rem] text-sm">
            <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
              <tr>
                <th className="px-3 py-2.5 font-medium">매입번호</th>
                <th className="px-3 py-2.5 font-medium">매입일</th>
                <th className="px-3 py-2.5 font-medium">매입처</th>
                <th className="px-3 py-2.5 font-medium">입고 위치</th>
                <th className="px-3 py-2.5 text-right font-medium">품목 / 수량</th>
                <th className="px-3 py-2.5 text-right font-medium">매입 금액</th>
                <th className="px-3 py-2.5 font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((p) => (
                <tr key={p.id} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                  <td className="px-3 py-2">
                    <Link href={`/purchases/${p.id}`} className="font-mono text-xs font-medium hover:underline">
                      {p.purchaseNo}
                    </Link>
                  </td>
                  <td className="px-3 py-2 tabular-nums">{p.purchasedOn}</td>
                  <td className="px-3 py-2">{p.supplierName ?? <span className="text-ink-faint">-</span>}</td>
                  <td className="px-3 py-2">{p.locationName}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {p.itemCount}종 / {p.totalQty.toLocaleString("ko-KR")}개
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{formatWon(p.totalCost)}</td>
                  <td className="px-3 py-2">
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${PURCHASE_STATUS_STYLE[p.status]}`}>
                      {PURCHASE_STATUS_LABEL[p.status]}
                    </span>
                  </td>
                </tr>
              ))}
              {data.items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-3 py-12 text-center text-ink-faint">
                    매입 내역이 없습니다. 상품을 들여오면 &lsquo;매입 등록&rsquo;으로 입고하세요.
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
