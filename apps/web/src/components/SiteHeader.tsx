import Link from "next/link";
import { Suspense } from "react";

import { AuthMenu } from "./AuthMenu";
import { SearchBox } from "./SearchBox";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-surface/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 sm:gap-6 sm:px-6">
        <Link href="/" className="shrink-0 text-lg font-bold tracking-tight text-brand">
          AIShop
          <span className="ml-1.5 hidden text-xs font-medium text-ink-faint sm:inline">
            반품·리퍼 아울렛
          </span>
        </Link>
        <Suspense fallback={<div className="h-10 flex-1" />}>
          <SearchBox />
        </Suspense>
        <nav className="flex shrink-0 items-center gap-3 text-sm text-ink-soft">
          <Link href="/cart" className="whitespace-nowrap hover:text-ink">
            장바구니
          </Link>
          <Suspense fallback={<span className="w-14" aria-hidden />}>
            <AuthMenu />
          </Suspense>
        </nav>
      </div>
    </header>
  );
}
