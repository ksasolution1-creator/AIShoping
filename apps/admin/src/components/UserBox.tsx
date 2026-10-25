"use client";

import Link from "next/link";

import { authApi } from "@/lib/api";
import { useMe } from "./AuthGate";

export function UserBox() {
  const me = useMe();
  if (!me) return null;

  async function logout() {
    await authApi.logout().catch(() => undefined);
    window.location.replace("/login");
  }

  return (
    // 휴대폰에서는 메뉴 아래 한 줄로, 넓은 화면에서는 왼쪽 메뉴 맨 아래에 보인다
    <div className="flex items-center gap-3 border-t border-line px-4 py-2 text-sm md:flex-col md:items-stretch md:gap-1 md:px-5 md:py-4">
      <span className="font-medium">{me.name}</span>
      <span className="hidden truncate text-xs text-ink-faint md:block">{me.email}</span>
      <div className="ml-auto flex gap-3 text-xs text-ink-soft md:mt-1 md:ml-0">
        <Link href="/account" className="underline underline-offset-4">
          비밀번호 변경
        </Link>
        <button type="button" onClick={logout} className="underline underline-offset-4">
          로그아웃
        </button>
      </div>
    </div>
  );
}
