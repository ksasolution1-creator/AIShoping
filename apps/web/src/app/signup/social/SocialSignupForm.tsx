"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { AgreementFields, missingAgreements, NO_AGREEMENTS, type Agreements } from "@/components/AgreementFields";
import { TextField } from "@/components/AuthCard";
import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { authApi, ClientApiError, safeNext, type SocialPending } from "@/lib/client-api";

/** 간편로그인으로 처음 온 회원: 이름 확인과 약관 동의만 받고 가입한다 */
export function SocialSignupForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [pending, setPending] = useState<SocialPending | null | undefined>(undefined);
  const [agreements, setAgreements] = useState<Agreements>(NO_AGREEMENTS);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    authApi
      .socialPending()
      .then(setPending)
      .catch(() => setPending(null));
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    const localErrors = missingAgreements(agreements);
    if (!name) localErrors.name = "이름을 입력해 주세요.";
    setErrors(localErrors);
    if (Object.keys(localErrors).length) {
      setMessage("빨간 글씨로 표시된 항목을 확인해 주세요.");
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      await authApi.socialSignup({ name, ...agreements });
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

  if (pending === undefined) return <WhaleLoader />;
  if (pending === null) {
    return (
      <div className="flex flex-col items-center gap-4 text-center text-sm text-ink-soft">
        <p>간편로그인 정보가 만료됐습니다. 처음부터 다시 시도해 주세요.</p>
        <Link href={`/login?next=${encodeURIComponent(next)}`} className="font-medium text-brand underline underline-offset-4">
          로그인 화면으로
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <p className="rounded-md bg-surface-muted p-3 text-sm text-ink-soft">
        <strong className="text-ink">{pending.providerLabel}</strong> 계정으로 처음 오셨네요. 약관에 동의하시면 바로
        가입됩니다.
        {pending.email && <span className="mt-1 block text-xs">이메일: {pending.email}</span>}
      </p>
      <TextField
        id="name"
        label="이름 (주문·배송에 쓰입니다)"
        autoComplete="name"
        defaultValue={pending.name ?? ""}
        required
        error={errors.name}
      />
      <AgreementFields
        value={agreements}
        onChange={setAgreements}
        errors={errors}
        collected={`${pending.providerLabel} 회원번호, 이름${pending.email ? ", 이메일" : ""}`}
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
        {submitting ? "가입 중…" : "동의하고 가입하기"}
      </button>
    </form>
  );
}
