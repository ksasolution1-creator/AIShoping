import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ClaimBoard } from "./ClaimBoard";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "취소·반품" };

export default function ClaimsPage() {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-bold">취소·반품</h1>
        <p className="text-sm text-ink-faint">
          반품은 신청 → 승인(회수) → 검수 순서로 처리합니다. 검수할 때 상태등급을 다시 매기면 그 등급으로 재입고되고 환불됩니다.
        </p>
      </div>
      <Suspense fallback={<WhaleLoader />}>
        <ClaimBoard />
      </Suspense>
    </div>
  );
}
