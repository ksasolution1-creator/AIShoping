"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { SocialLoginPlaceholder, TextField } from "@/components/AuthCard";
import { authApi, ClientApiError, safeNext } from "@/lib/client-api";

type AgreementKey = "agreeAge14" | "agreeTerms" | "agreePrivacy" | "agreeMarketing";

const AGREEMENTS: { key: AgreementKey; label: string; required: boolean; detail?: string }[] = [
  { key: "agreeAge14", label: "만 14세 이상입니다", required: true },
  { key: "agreeTerms", label: "이용약관 동의", required: true, detail: "약관 본문은 사업자 정보 확정 후 게시합니다." },
  {
    key: "agreePrivacy",
    label: "개인정보 수집·이용 동의",
    required: true,
    detail: "수집 항목: 이메일, 이름, 비밀번호. 목적: 회원 식별, 주문·배송, 고객 상담. 보유 기간: 탈퇴 시까지 (거래 기록은 관련 법에 따라 5년).",
  },
  {
    key: "agreeMarketing",
    label: "할인·이벤트 소식 받기",
    required: false,
    detail: "동의하지 않아도 가입할 수 있으며, 마이페이지에서 언제든 바꿀 수 있습니다.",
  },
];

export function SignupForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [agreements, setAgreements] = useState<Record<AgreementKey, boolean>>({
    agreeAge14: false,
    agreeTerms: false,
    agreePrivacy: false,
    agreeMarketing: false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const allChecked = AGREEMENTS.every((a) => agreements[a.key]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") ?? "");
    const localErrors: Record<string, string> = {};
    if (password !== String(form.get("passwordConfirm") ?? "")) {
      localErrors.passwordConfirm = "비밀번호가 서로 다릅니다.";
    }
    for (const a of AGREEMENTS) {
      if (a.required && !agreements[a.key]) localErrors[a.key] = "필수 항목입니다. 동의해 주세요.";
    }
    setErrors(localErrors);
    if (Object.keys(localErrors).length) {
      setMessage("빨간 글씨로 표시된 항목을 확인해 주세요.");
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      await authApi.signup({
        email: String(form.get("email") ?? "").trim(),
        password,
        name: String(form.get("name") ?? "").trim(),
        ...agreements,
      });
      router.replace(next);
      router.refresh();
    } catch (e) {
      if (e instanceof ClientApiError) {
        setErrors(e.fieldErrors);
        setMessage(e.message);
      } else {
        setMessage("가입하지 못했습니다.");
      }
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <TextField id="email" label="이메일" type="email" autoComplete="email" required error={errors.email} />
        <TextField
          id="password"
          label="비밀번호 (영문·숫자 포함 8자 이상)"
          type="password"
          autoComplete="new-password"
          required
          error={errors.password}
        />
        <TextField
          id="passwordConfirm"
          label="비밀번호 확인"
          type="password"
          autoComplete="new-password"
          required
          error={errors.passwordConfirm}
        />
        <TextField id="name" label="이름" autoComplete="name" required error={errors.name} />

        <fieldset className="flex flex-col gap-2 rounded-md border border-line p-4">
          <legend className="sr-only">약관 동의</legend>
          <label className="flex items-center gap-2 border-b border-line pb-3 font-semibold">
            <input
              type="checkbox"
              checked={allChecked}
              onChange={(e) =>
                setAgreements({
                  agreeAge14: e.target.checked,
                  agreeTerms: e.target.checked,
                  agreePrivacy: e.target.checked,
                  agreeMarketing: e.target.checked,
                })
              }
              className="size-4 accent-brand"
            />
            전체 동의
          </label>
          {AGREEMENTS.map((a) => (
            <div key={a.key} className="flex flex-col gap-1">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={agreements[a.key]}
                  onChange={(e) => setAgreements((prev) => ({ ...prev, [a.key]: e.target.checked }))}
                  className="size-4 accent-brand"
                />
                <span className={a.required ? "" : "text-ink-soft"}>
                  [{a.required ? "필수" : "선택"}] {a.label}
                </span>
              </label>
              {a.detail && <p className="pl-6 text-xs leading-relaxed text-ink-faint">{a.detail}</p>}
              {errors[a.key] && <p className="pl-6 text-xs text-sale">{errors[a.key]}</p>}
            </div>
          ))}
        </fieldset>

        {message && (
          <p role="alert" className="rounded-md bg-sale/10 p-3 text-sm text-sale">
            {message}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-50"
        >
          {submitting ? "가입 중…" : "가입하기"}
        </button>
      </form>
      <SocialLoginPlaceholder />
    </div>
  );
}
