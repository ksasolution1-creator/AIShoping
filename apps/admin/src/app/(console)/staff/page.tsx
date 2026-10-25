import type { Metadata } from "next";

import { StaffManager } from "./StaffManager";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "운영자 계정" };

export default function StaffPage() {
  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <h1 className="text-xl font-bold">운영자 계정</h1>
      <StaffManager />
    </div>
  );
}
