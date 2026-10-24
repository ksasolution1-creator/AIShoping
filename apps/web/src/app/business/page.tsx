"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { TextField } from "@/components/AuthCard";
import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { businessApi, ClientApiError, loginUrl, type BusinessProfile } from "@/lib/client-api";

const STATUS_TEXT: Record<BusinessProfile["status"], string> = {
  NONE: "",
  PENDING: "심사 중입니다. 운영자가 확인한 뒤 결과를 이 화면에 알려 드립니다.",
  APPROVED: "사업자 인증을 마쳤습니다. 사업자 회원가와 사업자 전용 상품을 이용할 수 있습니다.",
  REJECTED: "인증이 거절되었습니다. 사유를 확인하고 다시 신청해 주세요.",
};

export default function BusinessPage() {
  const router = useRouter();
  const [profile, setProfile] = useState<BusinessProfile | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    businessApi
      .profile()
      .then(setProfile)
      .catch((e) => {
        if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
        else setMessage("정보를 불러오지 못했습니다.");
      });
  }, [router]);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setBusy(true);
    setErrors({});
    setMessage(null);
    try {
      setProfile(
        await businessApi.apply({
          businessNo: String(data.get("businessNo")),
          businessName: String(data.get("businessName")),
          ceoName: String(data.get("ceoName")),
          businessItem: String(data.get("businessItem") ?? "") || null,
        }),
      );
    } catch (err) {
      if (err instanceof ClientApiError) {
        setErrors(err.fieldErrors);
        setMessage(err.message);
      } else {
        setMessage("신청하지 못했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  if (!profile) return message ? <p className="py-10 text-center text-sm text-sale">{message}</p> : <WhaleLoader />;
  const canApply = profile.status === "NONE" || profile.status === "REJECTED";

  return (
    <div className="mx-auto flex w-full max-w-lg flex-col gap-6 py-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-bold">사업자 회원 인증</h1>
        <p className="text-sm text-ink-soft">
          사업자 인증을 받으면 사업자 회원가로 사고, 사업자 전용 상품도 구매할 수 있습니다.
        </p>
      </div>

      {profile.status !== "NONE" && (
        <div
          className={`flex flex-col gap-1 rounded-md p-4 text-sm ${
            profile.status === "APPROVED" ? "bg-brand/10 text-brand" : profile.status === "REJECTED" ? "bg-sale/10 text-sale" : "bg-surface-muted"
          }`}
        >
          <p className="font-semibold">{STATUS_TEXT[profile.status]}</p>
          <p className="text-ink-soft">
            {profile.businessName} · {profile.businessNo?.replace(/(\d{3})(\d{2})(\d{5})/, "$1-$2-$3")} · 대표 {profile.ceoName}
          </p>
          {profile.rejectReason && <p>거절 사유: {profile.rejectReason}</p>}
        </div>
      )}

      {canApply && (
        <form onSubmit={submit} className="flex flex-col gap-4">
          <TextField
            id="businessNo"
            label="사업자등록번호"
            inputMode="numeric"
            placeholder="000-00-00000"
            defaultValue={profile.businessNo ?? ""}
            required
            error={errors.businessNo}
          />
          <TextField id="businessName" label="상호" defaultValue={profile.businessName ?? ""} required error={errors.businessName} />
          <TextField id="ceoName" label="대표자" defaultValue={profile.ceoName ?? ""} required error={errors.ceoName} />
          <TextField
            id="businessItem"
            label="업태·종목 (선택)"
            placeholder="예: 도소매 / 잡화"
            defaultValue={profile.businessItem ?? ""}
            error={errors.businessItem}
          />
          <p className="text-xs text-ink-faint">
            입력한 정보는 국세청 사업자 상태 확인 등 인증 심사에만 씁니다. 사업자등록증과 다르면 승인되지 않습니다.
          </p>
          {message && <p className="text-sm text-sale">{message}</p>}
          <button type="submit" disabled={busy} className="h-12 rounded-md bg-brand font-semibold text-white disabled:opacity-50">
            {profile.status === "REJECTED" ? "다시 신청하기" : "인증 신청하기"}
          </button>
        </form>
      )}
    </div>
  );
}
