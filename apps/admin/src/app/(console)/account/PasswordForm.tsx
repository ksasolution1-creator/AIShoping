"use client";

import { useState } from "react";

import { useMe } from "@/components/AuthGate";
import { ApiError, authApi } from "@/lib/api";

// 운영자 비밀번호 최소 길이 (API AuthService.STAFF_MIN_PASSWORD_LENGTH 와 같게)
const MIN_LENGTH = 10;

export function PasswordForm() {
  const me = useMe();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const current = String(data.get("current"));
    const next = String(data.get("next"));
    if (next !== data.get("confirm")) {
      setError("새 비밀번호와 확인 값이 다릅니다.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setDone(false);
    try {
      await authApi.changePassword(current, next);
      form.reset();
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(Object.values(err.fieldErrors)[0] ?? err.message);
      } else {
        setError("비밀번호를 바꾸지 못했습니다.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex max-w-md flex-col gap-5">
      <h1 className="text-xl font-bold">비밀번호 변경</h1>
      <form onSubmit={submit} className="card flex flex-col gap-3 p-5">
        {/* 비밀번호 관리자가 어느 계정의 비밀번호인지 알 수 있게 아이디를 함께 둔다 */}
        <input type="email" name="username" autoComplete="username" value={me?.email ?? ""} readOnly hidden />
        <label className="flex flex-col gap-1">
          <span className="field-label">현재 비밀번호</span>
          <input name="current" type="password" autoComplete="current-password" required className="input h-10" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">새 비밀번호</span>
          <input
            name="next"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_LENGTH}
            maxLength={64}
            className="input h-10"
          />
          <span className="text-xs text-ink-faint">영문과 숫자를 섞어 {MIN_LENGTH}자 이상</span>
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">새 비밀번호 확인</span>
          <input
            name="confirm"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_LENGTH}
            maxLength={64}
            className="input h-10"
          />
        </label>
        {error && (
          <p role="alert" className="rounded bg-danger/10 p-2.5 text-sm text-danger">
            {error}
          </p>
        )}
        {done && (
          <p role="status" className="rounded bg-ok/10 p-2.5 text-sm text-ok">
            비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요.
          </p>
        )}
        <button type="submit" className="btn-primary h-10" disabled={submitting}>
          {submitting ? "바꾸는 중…" : "비밀번호 변경"}
        </button>
      </form>
    </div>
  );
}
