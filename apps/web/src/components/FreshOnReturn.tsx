"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef } from "react";

/** 탭을 이만큼 이상 떠나 있다가 돌아오면 새로 불러온다 */
const AWAY_MS = 30_000;

/**
 * 뒤로·앞으로 가기와 오래 열어 둔 탭은 전에 받아 둔 화면을 그대로 보여 준다 (Next.js 클라이언트 캐시, 브라우저 bfcache).
 * 그러면 판매관리에서 방금 올린 상품·바꾼 가격이 안 보이므로, 그런 경우에만 서버에서 다시 받아 온다.
 */
export function FreshOnReturn() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const popped = useRef(false);

  useEffect(() => {
    const onPop = () => {
      popped.current = true;
    };
    // 브라우저가 페이지를 통째로 보관했다가 되살린 경우
    const onPageShow = (e: PageTransitionEvent) => {
      if (e.persisted) router.refresh();
    };
    let hiddenAt = 0;
    const onVisibility = () => {
      if (document.visibilityState === "hidden") {
        hiddenAt = Date.now();
      } else if (hiddenAt && Date.now() - hiddenAt >= AWAY_MS) {
        hiddenAt = 0;
        router.refresh();
      }
    };
    window.addEventListener("popstate", onPop);
    window.addEventListener("pageshow", onPageShow);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.removeEventListener("popstate", onPop);
      window.removeEventListener("pageshow", onPageShow);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [router]);

  // 뒤로·앞으로 가기로 주소가 바뀐 뒤에 그 화면의 데이터를 새로 받는다
  useEffect(() => {
    if (!popped.current) return;
    popped.current = false;
    router.refresh();
  }, [pathname, search, router]);

  return null;
}
