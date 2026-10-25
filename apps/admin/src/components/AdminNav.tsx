import Link from "next/link";
import { Suspense } from "react";

import { WhaleMark } from "./brand/WhaleMark";
import { ActiveNavLinks, NavLinks } from "./NavLinks";
import { UserBox } from "./UserBox";

export function AdminNav() {
  return (
    <aside className="flex flex-col border-b border-line bg-surface md:min-h-screen md:w-52 md:shrink-0 md:border-r md:border-b-0">
      <div className="px-4 py-4 md:px-5">
        <Link href="/orders" className="flex items-center gap-2">
          <WhaleMark className="size-9" />
          <span className="flex flex-col leading-tight">
            <span className="font-bold text-ink">떨고래 마켓</span>
            <span className="text-xs text-ink-faint">판매관리</span>
          </span>
        </Link>
      </div>
      <nav className="scrollbar-none overflow-x-auto px-2 pb-2 md:flex-1 md:pb-0">
        {/* 동적 경로(/products/[id])에서는 현재 주소를 요청 시점에 알 수 있어서 Suspense로 감싼다 */}
        <Suspense fallback={<NavLinks pathname="" />}>
          <ActiveNavLinks />
        </Suspense>
      </nav>
      <UserBox />
    </aside>
  );
}
