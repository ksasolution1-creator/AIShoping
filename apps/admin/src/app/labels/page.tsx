import type { Metadata } from "next";

import { AuthGate } from "@/components/AuthGate";
import { LabelSheet } from "./LabelSheet";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "바코드 라벨 인쇄" };

/** 인쇄 전용 화면이라 왼쪽 메뉴 없이 연다 */
export default function LabelsPage() {
  return (
    <AuthGate>
      <LabelSheet />
    </AuthGate>
  );
}
