import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ShippingBoard } from "./ShippingBoard";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "배송·송장" };

export default function ShippingPage() {
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-xl font-bold">배송·송장</h1>
      <Suspense fallback={<WhaleLoader />}>
        <ShippingBoard />
      </Suspense>
    </div>
  );
}
