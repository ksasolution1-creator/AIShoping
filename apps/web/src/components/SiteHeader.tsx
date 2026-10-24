import Link from "next/link";
import { Suspense } from "react";

import { AuthMenu } from "./AuthMenu";
import { WhaleMark } from "./brand/WhaleMark";
import { SearchBox } from "./SearchBox";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 bg-surface/95 shadow-[0_1px_0_var(--color-line)] backdrop-blur">
      <p className="bg-navy px-4 py-1.5 text-center text-xs text-foam">
        오늘도 떨고래에서 <strong className="font-semibold text-tag">땡잡으세요!</strong> · 5만 원 이상 무료배송
      </p>
      {/* 모바일: 로고·메뉴 한 줄, 검색창은 아래 줄 전체 너비 */}
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3 sm:flex-nowrap sm:gap-6 sm:px-6">
        <Link href="/" className="flex shrink-0 items-center gap-1.5" aria-label="떨고래 마켓 홈">
          <WhaleMark className="size-10 sm:size-11" />
          <span className="flex flex-col leading-none">
            <span className="font-display text-xl text-navy sm:text-2xl">떨고래 마켓</span>
            <span className="mt-1 hidden w-fit rounded-full bg-tag px-1.5 py-0.5 text-[10px] font-bold text-navy sm:block">
              재고 · 땡처리 도매방
            </span>
          </span>
        </Link>
        <div className="order-last flex w-full sm:order-none sm:w-auto sm:flex-1">
          <Suspense fallback={<div className="h-11 flex-1" />}>
            <SearchBox />
          </Suspense>
        </div>
        <nav className="ml-auto flex shrink-0 items-center gap-3 text-sm font-medium text-ink-soft sm:ml-0">
          <Link href="/cart" className="whitespace-nowrap hover:text-brand">
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
