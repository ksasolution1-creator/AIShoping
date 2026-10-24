import type { Metadata } from "next";

import { PasswordForm } from "./PasswordForm";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "비밀번호 변경" };

export default function AccountPage() {
  return <PasswordForm />;
}
