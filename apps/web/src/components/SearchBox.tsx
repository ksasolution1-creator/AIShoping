"use client";

import { useRouter, useSearchParams } from "next/navigation";

export function SearchBox() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const current = searchParams.get("q") ?? "";

  return (
    <form
      role="search"
      className="flex flex-1"
      onSubmit={(event) => {
        event.preventDefault();
        const q = String(new FormData(event.currentTarget).get("q") ?? "").trim();
        router.push(q ? `/?q=${encodeURIComponent(q)}` : "/");
      }}
    >
      <label htmlFor="site-search" className="sr-only">
        상품 검색
      </label>
      <input
        id="site-search"
        key={current}
        name="q"
        type="search"
        defaultValue={current}
        placeholder="상품명, 브랜드 검색"
        className="h-10 w-full min-w-0 rounded-md border border-line bg-surface-muted px-3 text-sm outline-none focus:border-brand focus:bg-surface"
      />
    </form>
  );
}
