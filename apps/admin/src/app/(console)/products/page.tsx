import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { ProductList } from "./ProductList";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "상품 관리" };

export default function ProductsPage() {
  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl font-bold">상품 관리</h1>
        <Link href="/products/new" className="btn-primary">
          + 상품 등록
        </Link>
      </div>
      <Suspense fallback={<p className="text-sm text-ink-faint">불러오는 중…</p>}>
        <ProductList />
      </Suspense>
    </div>
  );
}
