import type { Metadata } from "next";
import Link from "next/link";

import { PurchaseEditor } from "../PurchaseEditor";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "매입 등록" };

export default function NewPurchasePage() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <Link href="/purchases" className="text-sm text-ink-soft hover:underline">
          ← 매입 목록
        </Link>
        <h1 className="text-xl font-bold">매입 등록</h1>
      </div>
      <PurchaseEditor initial={null} />
    </div>
  );
}
