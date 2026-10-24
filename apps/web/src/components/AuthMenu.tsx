"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import { authApi, type Me } from "@/lib/client-api";

/** 헤더 오른쪽 로그인 상태. 페이지를 옮길 때마다 다시 확인한다 */
export function AuthMenu() {
  const pathname = usePathname();
  const [me, setMe] = useState<Me | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    authApi
      .me()
      .then((user) => !cancelled && setMe(user))
      .catch(() => !cancelled && setMe(null));
    return () => {
      cancelled = true;
    };
  }, [pathname]);

  if (me === undefined) return <span className="w-14" aria-hidden />;

  if (!me) {
    const next = pathname && pathname !== "/login" && pathname !== "/signup" ? `?next=${encodeURIComponent(pathname)}` : "";
    return (
      <Link href={`/login${next}`} className="text-sm whitespace-nowrap text-ink-soft hover:text-ink">
        로그인
      </Link>
    );
  }

  return (
    <Link href="/mypage" className="max-w-24 truncate text-sm whitespace-nowrap text-ink-soft hover:text-ink">
      {me.name}님
    </Link>
  );
}
