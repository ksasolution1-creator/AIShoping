"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";

import { ADMIN_ROLES, ApiError, authApi } from "@/lib/api";

function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login") ? next : "/orders";
}

export function LoginForm() {
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [error, setError] = useState<string | null>(
    params.get("denied") ? "운영자 권한이 있는 계정으로 로그인해 주세요." : null,
  );
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      const me = await authApi.login(String(form.get("email") ?? ""), String(form.get("password") ?? ""));
      if (me.type !== "STAFF" || !me.roles.some((r) => ADMIN_ROLES.includes(r))) {
        await authApi.logout().catch(() => undefined);
        setError("운영자 권한이 없는 계정입니다.");
        setSubmitting(false);
        return;
      }
      window.location.replace(next);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "로그인하지 못했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <label className="flex flex-col gap-1">
        <span className="field-label">이메일</span>
        <input id="email" name="email" type="email" autoComplete="username" required className="input h-10" />
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">비밀번호</span>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className="input h-10"
        />
      </label>
      {error && (
        <p role="alert" className="rounded bg-danger/10 p-2.5 text-sm text-danger">
          {error}
        </p>
      )}
      <button type="submit" className="btn-primary h-10" disabled={submitting}>
        {submitting ? "로그인 중…" : "로그인"}
      </button>
    </form>
  );
}
