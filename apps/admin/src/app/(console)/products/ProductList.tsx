"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ConfirmButton } from "@/components/ConfirmButton";
import { GradeQuickEdit, gradeLabel } from "@/components/GradeQuickEdit";
import { PriceQuickEdit } from "@/components/PriceQuickEdit";
import { StatusChip } from "@/components/StatusChip";
import { StockQuickEdit } from "@/components/StockQuickEdit";
import { Thumb } from "@/components/Thumb";
import {
  api,
  formatDate,
  PRODUCT_STATUS_LABEL,
  type AdminProductRow,
  type ListSku,
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
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [reloadKey, setReloadKey] = useState(0);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(
    params.get("deleted") === "1" ? { kind: "ok", text: "상품을 삭제했습니다." } : null,
  );

  useEffect(() => {
    let cancelled = false;
    api
      .products({ q, status, page })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
          setSelected(new Set());
        }
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [q, status, page, reloadKey]);

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function deleteSelected() {
    try {
      const result = await api.deleteProducts([...selected]);
      setNotice(
        result.failed.length === 0
          ? { kind: "ok", text: `${result.deleted}개 상품을 삭제했습니다.` }
          : {
              kind: "error",
              text:
                `${result.deleted}개 삭제, ${result.failed.length}개는 지우지 못했습니다: ` +
                result.failed.map((f) => `#${f.id} ${f.reason}`).join(" / "),
            },
      );
      setReloadKey((k) => k + 1);
    } catch (e) {
      setNotice({ kind: "error", text: e instanceof Error ? e.message : "삭제하지 못했습니다." });
    }
  }

  /** 등급·판매가·재고를 바꾼 뒤 목록을 다시 부르지 않고 그 줄만 바꾼다 */
  function updateSku(productId: number, skuId: number, change: Partial<ListSku>) {
    setData((d) =>
      d && {
        ...d,
        items: d.items.map((row) =>
          row.id !== productId
            ? row
            : { ...row, skus: row.skus.map((s) => (s.skuId === skuId ? { ...s, ...change } : s)) },
        ),
      },
    );
  }

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
      {notice && (
        <p
          role="status"
          className={`rounded p-3 text-sm ${notice.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
        >
          {notice.text}
        </p>
      )}
      {selected.size > 0 && (
        <div className="card flex flex-wrap items-center gap-3 p-3 text-sm">
          <span className="font-medium">{selected.size}개 선택</span>
          <ConfirmButton
            label="선택 삭제"
            question={`${selected.size}개 상품을 삭제할까요?`}
            onConfirm={deleteSelected}
          />
          <button type="button" className="btn-outline" onClick={() => setSelected(new Set())}>
            선택 해제
          </button>
        </div>
      )}

      <div className="card overflow-x-auto">
        <table className="w-full min-w-[52rem] text-sm">
          <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
            <tr>
              <th className="w-10 px-3 py-2.5">
                <input
                  type="checkbox"
                  aria-label="이 페이지 전체 선택"
                  checked={!!data?.items.length && data.items.every((p) => selected.has(p.id))}
                  onChange={(e) => setSelected(e.target.checked ? new Set(data?.items.map((p) => p.id)) : new Set())}
                />
              </th>
              <th className="px-3 py-2.5 font-medium">상품</th>
              <th className="px-3 py-2.5 font-medium">카테고리</th>
              <th className="px-3 py-2.5 font-medium">상태</th>
              <th className="px-3 py-2.5 font-medium">상태등급 · 판매가 · 재고 (눌러서 변경)</th>
              <th className="px-3 py-2.5 font-medium">등록일</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((p) => (
              <tr key={p.id} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                <td className="px-3 py-2">
                  <input
                    type="checkbox"
                    aria-label={`${p.name} 선택`}
                    checked={selected.has(p.id)}
                    onChange={() => toggle(p.id)}
                  />
                </td>
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
                <td className="px-3 py-2">
                  <ul className="flex min-w-96 flex-col gap-1">
                    {p.skus.map((sku) => (
                      <li key={sku.skuId} className="flex items-center gap-2">
                        <GradeQuickEdit
                          sku={sku}
                          onSaved={(code) => updateSku(p.id, sku.skuId, { grade: { code, label: gradeLabel(code) } })}
                        />
                        <span className="ml-auto">
                          <PriceQuickEdit sku={sku} onSaved={(price) => updateSku(p.id, sku.skuId, { price })} />
                        </span>
                        <span className="w-28 text-right">
                          <StockQuickEdit sku={sku} onSaved={(stock) => updateSku(p.id, sku.skuId, stock)} />
                        </span>
                      </li>
                    ))}
                    {p.skus.length === 0 && <li className="text-ink-faint">판매 단위 없음</li>}
                  </ul>
                </td>
                <td className="px-3 py-2 text-xs text-ink-faint tabular-nums">{formatDate(p.createdAt)}</td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-12 text-center text-ink-faint">
                  상품이 없습니다. 오른쪽 위 &lsquo;상품 등록&rsquo;으로 첫 상품을 등록하세요.
                </td>
              </tr>
            )}
            {!data && !error && (
              <tr>
                <td colSpan={6}>
                  <WhaleLoader size="sm" label="상품을 불러오는 중이에요" />
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
