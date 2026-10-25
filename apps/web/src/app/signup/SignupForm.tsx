"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { AgreementFields, missingAgreements, NO_AGREEMENTS, type Agreements } from "@/components/AgreementFields";
import { TextField } from "@/components/AuthCard";
import { SocialLoginButtons } from "@/components/SocialLoginButtons";
import { authApi, ClientApiError, safeNext } from "@/lib/client-api";

export function SignupForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [agreements, setAgreements] = useState<Agreements>(NO_AGREEMENTS);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") ?? "");
    const localErrors: Record<string, string> = missingAgreements(agreements);
    if (password !== String(form.get("passwordConfirm") ?? "")) {
      localErrors.passwordConfirm = "비밀번호가 서로 다릅니다.";
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

        <AgreementFields
          value={agreements}
          onChange={setAgreements}
          errors={errors}
          collected="이메일, 이름, 비밀번호"
        />

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
      <SocialLoginButtons next={next} />
    </div>
  );
}
