"use client";

import { useState } from "react";

import { TextField } from "@/components/AuthCard";
import { authApi, ClientApiError } from "@/lib/client-api";

/** 마이페이지 비밀번호 변경 (펼쳐서 입력) */
export function PasswordChange({ email }: { email: string | null }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = String(data.get("new-password"));
    if (next !== data.get("new-password-confirm")) {
      setError("새 비밀번호와 확인 값이 다릅니다.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await authApi.changePassword(String(data.get("current-password")), next);
      setOpen(false);
      setDone(true);
    } catch (e) {
      if (e instanceof ClientApiError) {
        setError(Object.values(e.fieldErrors)[0] ?? e.message);
      } else {
        setError("비밀번호를 바꾸지 못했습니다.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => {
          setOpen(true);
          setDone(false);
        }}
        className="flex items-center justify-between px-4 py-3.5 text-left text-sm hover:bg-surface-muted"
      >
        <span>
          비밀번호 변경
          {done && <span className="ml-2 text-xs text-brand">변경했습니다</span>}
        </span>
        <span className="text-ink-faint">›</span>
      </button>
    );
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3 bg-surface-muted p-4">
      <h2 className="font-semibold">비밀번호 변경</h2>
      {/* 비밀번호 관리자가 어느 계정의 비밀번호인지 알 수 있게 아이디를 함께 둔다 */}
      <input type="email" name="username" autoComplete="username" value={email ?? ""} readOnly hidden />
      <TextField id="current-password" label="현재 비밀번호" type="password" autoComplete="current-password" required />
      <TextField
        id="new-password"
        label="새 비밀번호 (영문·숫자 포함 8자 이상)"
        type="password"
        autoComplete="new-password"
        minLength={8}
        maxLength={64}
        required
      />
      <TextField
        id="new-password-confirm"
        label="새 비밀번호 확인"
        type="password"
        autoComplete="new-password"
        minLength={8}
        maxLength={64}
        required
        error={error ?? undefined}
      />
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            setError(null);
          }}
          className="h-10 flex-1 rounded-md border border-line bg-surface text-sm"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="h-10 flex-1 rounded-md bg-brand text-sm font-semibold text-white disabled:opacity-60"
        >
          {submitting ? "바꾸는 중…" : "변경하기"}
        </button>
      </div>
    </form>
  );
}
