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
    <div className="hidden flex-col gap-1 border-t border-line px-5 py-4 text-sm md:flex">
      <span className="font-medium">{me.name}</span>
      <span className="truncate text-xs text-ink-faint">{me.email}</span>
      <div className="mt-1 flex gap-3 text-xs text-ink-soft">
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
