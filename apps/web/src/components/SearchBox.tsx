"use client";

import { useRouter, useSearchParams } from "next/navigation";

export function SearchBox() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const current = searchParams.get("q") ?? "";

  return (
    <form
      role="search"
      className="relative flex flex-1"
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
        placeholder="땡처리 상품을 찾아보세요"
        className="h-11 w-full min-w-0 rounded-full border-2 border-brand/80 bg-surface pr-12 pl-4 text-sm outline-none placeholder:text-ink-faint focus:border-brand"
      />
      <button
        type="submit"
        aria-label="검색"
        className="absolute top-1/2 right-1.5 flex size-8 -translate-y-1/2 items-center justify-center rounded-full bg-brand text-white hover:bg-brand-strong"
      >
        <svg viewBox="0 0 20 20" className="size-4" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden>
          <circle cx="9" cy="9" r="6" />
          <path d="m14 14 4 4" strokeLinecap="round" />
        </svg>
      </button>
    </form>
  );
}
