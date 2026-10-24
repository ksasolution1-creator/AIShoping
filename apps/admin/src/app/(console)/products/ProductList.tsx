"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { StatusChip } from "@/components/StatusChip";
import { Thumb } from "@/components/Thumb";
import {
  api,
  formatDate,
  formatWon,
  PRODUCT_STATUS_LABEL,
  type AdminProductRow,
  type Page,
  type ProductStatus,
} from "@/lib/api";

const STATUS_FILTERS = (["DRAFT", "ON_SALE", "SOLD_OUT", "HIDDEN"] as ProductStatus[]).map((s) => ({
  value: s,
  label: PRODUCT_STATUS_LABEL[s],
}));

export function ProductList() {
  const router = useRouter();
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const status = params.get("status") ?? "";
  const page = Number(params.get("page") ?? 0) || 0;

  const [data, setData] = useState<Page<AdminProductRow> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .products({ q, status, page })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [q, status, page]);

  function go(change: { q?: string; status?: string; page?: number }) {
    const next = new URLSearchParams();
    const merged = { q, status, page: 0, ...change };
    if (merged.q) next.set("q", merged.q);
    if (merged.status) next.set("status", merged.status);
    if (merged.page) next.set("page", String(merged.page));
    router.push(`/products${next.size ? `?${next}` : ""}`);
  }

  return (
    <div className="flex flex-col gap-4">
      <form
        className="card flex flex-wrap items-end gap-3 p-3"
        onSubmit={(e) => {
          e.preventDefault();
          go({ q: String(new FormData(e.currentTarget).get("q") ?? "").trim() });
        }}
      >
        <div className="flex min-w-48 flex-1 flex-col gap-1">
          <label htmlFor="product-search" className="field-label">
            검색 (상품명, 브랜드, SKU 코드, 바코드)
          </label>
          <input id="product-search" key={q} name="q" defaultValue={q} className="input" />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="status-filter" className="field-label">
            상태
          </label>
          <select
            id="status-filter"
            value={status}
            onChange={(e) => go({ status: e.target.value })}
            className="input w-32"
          >
            <option value="">전체</option>
            {STATUS_FILTERS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className="btn-outline">
          검색
        </button>
      </form>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full min-w-[52rem] text-sm">
          <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
            <tr>
              <th className="px-3 py-2.5 font-medium">상품</th>
              <th className="px-3 py-2.5 font-medium">카테고리</th>
              <th className="px-3 py-2.5 font-medium">상태</th>
              <th className="px-3 py-2.5 text-right font-medium">SKU</th>
              <th className="px-3 py-2.5 text-right font-medium">최저가</th>
              <th className="px-3 py-2.5 text-right font-medium">재고 (예약)</th>
              <th className="px-3 py-2.5 font-medium">등록일</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((p) => (
              <tr key={p.id} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                <td className="px-3 py-2">
                  <Link href={`/products/${p.id}`} className="flex items-center gap-3">
                    <Thumb src={p.thumbnailUrl} className="size-11" />
                    <span className="flex flex-col">
                      <span className="font-medium hover:underline">{p.name}</span>
                      <span className="text-xs text-ink-faint">
                        #{p.id} {p.brand && `· ${p.brand}`}
                      </span>
                    </span>
                  </Link>
                </td>
                <td className="px-3 py-2 text-ink-soft">{p.categoryName ?? "-"}</td>
                <td className="px-3 py-2">
                  <StatusChip status={p.status} />
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{p.skuCount}</td>
                <td className="px-3 py-2 text-right tabular-nums">{formatWon(p.minPrice)}</td>
                <td className="px-3 py-2 text-right tabular-nums">
                  <span className={p.onHand - p.reserved <= 0 ? "font-semibold text-danger" : ""}>{p.onHand}</span>
                  {p.reserved > 0 && <span className="text-ink-faint"> ({p.reserved})</span>}
                </td>
                <td className="px-3 py-2 text-xs text-ink-faint tabular-nums">{formatDate(p.createdAt)}</td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr>
                <td colSpan={7} className="px-3 py-12 text-center text-ink-faint">
                  상품이 없습니다. 오른쪽 위 &lsquo;상품 등록&rsquo;으로 첫 상품을 등록하세요.
                </td>
              </tr>
            )}
            {!data && !error && (
              <tr>
                <td colSpan={7} className="px-3 py-12 text-center text-ink-faint">
                  불러오는 중…
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 text-sm">
          <button className="btn-outline" disabled={page <= 0} onClick={() => go({ page: page - 1 })}>
            이전
          </button>
          <span className="tabular-nums text-ink-soft">
            {page + 1} / {data.totalPages}
          </span>
          <button
            className="btn-outline"
            disabled={page + 1 >= data.totalPages}
            onClick={() => go({ page: page + 1 })}
          >
            다음
          </button>
        </div>
      )}
      {data && <p className="text-xs text-ink-faint">총 {data.total.toLocaleString("ko-KR")}개</p>}
    </div>
  );
}
