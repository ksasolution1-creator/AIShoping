"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { TextField } from "@/components/AuthCard";
import { SocialLoginButtons, socialErrorMessage } from "@/components/SocialLoginButtons";
import { authApi, ClientApiError, safeNext } from "@/lib/client-api";

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [error, setError] = useState<string | null>(socialErrorMessage(params.get("socialError")));
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await authApi.login(String(form.get("email") ?? ""), String(form.get("password") ?? ""));
      router.replace(next);
      router.refresh();
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "로그인하지 못했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextField id="email" label="이메일" type="email" autoComplete="email" required />
        <TextField id="password" label="비밀번호" type="password" autoComplete="current-password" required />
        {error && (
          <p role="alert" className="rounded-md bg-sale/10 p-3 text-sm text-sale">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-50"
        >
          {submitting ? "로그인 중…" : "로그인"}
        </button>
        <p className="text-center text-sm text-ink-soft">
          아직 회원이 아니신가요?{" "}
          <Link
            href={`/signup${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`}
            className="font-medium text-brand underline underline-offset-4"
          >
            회원가입
          </Link>
        </p>
      </form>
      <SocialLoginButtons next={next} />
    </div>
  );
}
