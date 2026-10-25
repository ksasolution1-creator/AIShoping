"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";

type Shortcut = { key: "best" | "ddaeng" | "new"; href: string; label: string };

const SHORTCUTS: Shortcut[] = [
  { key: "best", href: "/?sort=BEST", label: "베스트" },
  { key: "ddaeng", href: "/?ddaeng=1", label: "땡처리관" },
  { key: "new", href: "/", label: "신상품" },
];

/** 헤더 아래 바로가기. 지금 보고 있는 목록에 밑줄과 색을 준다 */
export function ShortcutNav() {
  const pathname = usePathname();
  const params = useSearchParams();

  let current: Shortcut["key"] | null = null;
  if (pathname === "/") {
    const sort = params.get("sort");
    if (params.get("ddaeng") === "1") current = "ddaeng";
    else if (sort === "BEST") current = "best";
    else if ((!sort || sort === "NEWEST") && !params.get("q")) current = "new";
  }

  return (
    <nav aria-label="바로가기" className="mx-auto flex max-w-6xl gap-5 overflow-x-auto px-4 text-sm font-semibold sm:px-6">
      {SHORTCUTS.map((s) => {
        const active = current === s.key;
        const color =
          s.key === "ddaeng"
            ? active
              ? "border-sale text-sale"
              : "border-transparent text-sale/80 hover:text-sale"
            : active
              ? "border-brand text-brand"
              : "border-transparent text-ink hover:text-brand";
        return (
          <Link
            key={s.key}
            href={s.href}
            aria-current={active ? "page" : undefined}
            className={`shrink-0 border-b-2 pt-0.5 pb-1.5 ${color}`}
          >
            {s.label}
          </Link>
        );
      })}
    </nav>
  );
}
