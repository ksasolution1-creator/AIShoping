import Link from "next/link";
import { Suspense } from "react";

import { HeroBanner } from "@/components/brand/HeroBanner";
import { ProductCardItem } from "@/components/ProductCardItem";
import {
  fetchCategories,
  fetchPopularKeywords,
  fetchProducts,
  type Category,
  type ProductSort,
} from "@/lib/api";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const SORTS: { value: ProductSort; label: string }[] = [
  { value: "NEWEST", label: "최신순" },
  { value: "BEST", label: "판매순" },
  { value: "PRICE_ASC", label: "낮은 가격순" },
  { value: "PRICE_DESC", label: "높은 가격순" },
];

const PAGE_SIZE = 24;

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function parseFilters(raw: Record<string, string | string[] | undefined>) {
  const categoryId = Number(first(raw.categoryId));
  const page = Number(first(raw.page));
  const sort = first(raw.sort) as ProductSort | undefined;
  return {
    q: first(raw.q)?.trim() || undefined,
    categoryId: Number.isInteger(categoryId) && categoryId > 0 ? categoryId : undefined,
    sort: SORTS.some((s) => s.value === sort) ? sort : "NEWEST",
    ddaeng: first(raw.ddaeng) === "1",
    page: Number.isInteger(page) && page > 0 ? page : 0,
  } as const;
}

type Filters = ReturnType<typeof parseFilters>;

function buildHref(filters: Filters, change: Partial<Filters>) {
  const next = { ...filters, page: 0, ...change };
  const params = new URLSearchParams();
  if (next.q) params.set("q", next.q);
  if (next.categoryId) params.set("categoryId", String(next.categoryId));
  if (next.sort && next.sort !== "NEWEST") params.set("sort", next.sort);
  if (next.ddaeng) params.set("ddaeng", "1");
  if (next.page) params.set("page", String(next.page));
  const query = params.toString();
  return query ? `/?${query}` : "/";
}

export default function HomePage({ searchParams }: { searchParams: SearchParams }) {
  return (
    <div className="flex flex-col gap-6 pt-5">
      <HeroBanner />

      <section className="flex flex-col gap-1">
        <h2 className="text-2xl font-bold text-navy">오늘의 땡처리</h2>
        <p className="text-sm text-ink-soft">
          모든 상품에 상태등급과 실물 사진을 표시합니다. 같은 상품도 상태에 따라 가격이 다릅니다.
        </p>
      </section>

      <Suspense fallback={<div className="h-9" />}>
        <CategoryTabs searchParams={searchParams} />
      </Suspense>

      <Suspense fallback={null}>
        <PopularKeywords />
      </Suspense>

      <Suspense fallback={<ProductGridSkeleton />}>
        <ProductResults searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function CategoryTabs({ searchParams }: { searchParams: SearchParams }) {
  const filters = parseFilters(await searchParams);
  let categories: Category[] = [];
  try {
    categories = await fetchCategories();
  } catch {
    return null;
  }
  const topLevel = categories.filter((c) => c.parentId === null);
  const tabs = [{ id: undefined as number | undefined, name: "전체" }, ...topLevel];

  return (
    <nav aria-label="카테고리" className="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <ul className="flex gap-2">
        {tabs.map((tab) => {
          const active = filters.categoryId === tab.id;
          return (
            <li key={tab.id ?? "all"}>
              <Link
                href={buildHref(filters, { categoryId: tab.id })}
                aria-current={active ? "page" : undefined}
                className={`block rounded-full border px-3.5 py-1.5 text-sm whitespace-nowrap ${
                  active
                    ? "border-brand bg-brand font-semibold text-white shadow-sm"
                    : "border-line bg-surface text-ink-soft hover:border-brand hover:text-brand"
                }`}
              >
                {tab.name}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

async function PopularKeywords() {
  let keywords: string[] = [];
  try {
    keywords = await fetchPopularKeywords();
  } catch {
    return null;
  }
  if (keywords.length === 0) return null;
  return (
    <nav aria-label="인기검색어" className="-mx-4 flex items-center gap-3 overflow-x-auto px-4 text-sm sm:mx-0 sm:px-0">
      <span className="shrink-0 font-semibold text-navy">인기검색어</span>
      <ol className="flex gap-3">
        {keywords.map((k, i) => (
          <li key={k} className="shrink-0">
            <Link href={`/?q=${encodeURIComponent(k)}`} className="text-ink-soft hover:text-brand">
              <span className="mr-1 font-bold text-brand tabular-nums">{i + 1}</span>
              {k}
            </Link>
          </li>
        ))}
      </ol>
    </nav>
  );
}

async function ProductResults({ searchParams }: { searchParams: SearchParams }) {
  const filters = parseFilters(await searchParams);
  const result = await fetchProducts({ ...filters, size: PAGE_SIZE });

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3 border-b border-line pb-3">
        <p className="text-sm text-ink-soft">
          {filters.ddaeng && <strong className="mr-1 text-sale">땡처리관 (40% 이상 할인)</strong>}
          {filters.q && (
            <>
              <strong className="text-ink">&lsquo;{filters.q}&rsquo;</strong> 검색 결과{" "}
            </>
          )}
          <span className="tabular-nums">{result.total.toLocaleString("ko-KR")}</span>개
        </p>
        <ul className="flex gap-3 text-sm">
          {SORTS.map((sort) => (
            <li key={sort.value}>
              <Link
                href={buildHref(filters, { sort: sort.value })}
                className={
                  filters.sort === sort.value ? "font-semibold text-ink" : "text-ink-faint hover:text-ink-soft"
                }
              >
                {sort.label}
              </Link>
            </li>
          ))}
        </ul>
      </div>

      {result.items.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-20 text-center">
          <p className="font-medium">조건에 맞는 상품이 없습니다.</p>
          <p className="text-sm text-ink-faint">검색어를 바꾸거나 다른 카테고리를 선택해 보세요.</p>
          <Link href="/" className="mt-2 text-sm text-brand underline underline-offset-4">
            전체 상품 보기
          </Link>
        </div>
      ) : (
        <ul className="grid grid-cols-2 gap-x-3 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {result.items.map((product) => (
            <li key={product.id}>
              <ProductCardItem product={product} />
            </li>
          ))}
        </ul>
      )}

      {result.totalPages > 1 && (
        <nav aria-label="페이지" className="flex justify-center gap-1 pt-6">
          {Array.from({ length: result.totalPages }, (_, i) => i).map((page) => (
            <Link
              key={page}
              href={buildHref(filters, { page, sort: filters.sort, categoryId: filters.categoryId })}
              aria-current={page === filters.page ? "page" : undefined}
              className={`flex size-9 items-center justify-center rounded-md text-sm tabular-nums ${
                page === filters.page ? "bg-brand text-white" : "text-ink-soft hover:bg-surface-muted"
              }`}
            >
              {page + 1}
            </Link>
          ))}
        </nav>
      )}
    </section>
  );
}

function ProductGridSkeleton() {
  return (
    <ul className="grid grid-cols-2 gap-x-3 gap-y-8 sm:grid-cols-3 lg:grid-cols-4" aria-hidden>
      {Array.from({ length: 8 }, (_, i) => (
        <li key={i} className="flex flex-col gap-2.5">
          <div className="aspect-square animate-pulse rounded-md bg-surface-muted" />
          <div className="h-4 w-3/4 animate-pulse rounded bg-surface-muted" />
          <div className="h-5 w-1/2 animate-pulse rounded bg-surface-muted" />
        </li>
      ))}
    </ul>
  );
}
