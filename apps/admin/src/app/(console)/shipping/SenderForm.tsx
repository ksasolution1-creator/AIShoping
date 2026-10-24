"use client";

import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ApiError, shippingApi, type SenderSettings } from "@/lib/api";

/** 송장에 찍히는 보내는 사람·출고지, 상품 상세에 안내할 반품지 */
export function SenderForm() {
  const [settings, setSettings] = useState<SenderSettings | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    shippingApi
      .sender()
      .then(setSettings)
      .catch((e: Error) => setMessage({ ok: false, text: e.message }));
  }, []);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const text = (key: string) => String(data.get(key) ?? "").trim() || null;
    try {
      const saved = await shippingApi.updateSender({
        senderName: text("senderName"),
        senderPhone: text("senderPhone"),
        shipFromZipcode: text("shipFromZipcode"),
        shipFromAddress: text("shipFromAddress"),
        returnZipcode: text("returnZipcode"),
        returnAddress: text("returnAddress"),
      });
      setSettings(saved);
      setMessage({ ok: true, text: "저장했습니다." });
    } catch (err) {
      setMessage({
        ok: false,
        text: err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "저장하지 못했습니다.",
      });
    }
  }

  if (!settings && !message) return <WhaleLoader />;

  return (
    <form onSubmit={submit} className="card flex max-w-2xl flex-col gap-4 p-5">
      <fieldset className="grid gap-3 sm:grid-cols-2">
        <legend className="mb-2 font-semibold">보내는 사람 (송장에 찍힘)</legend>
        <Field name="senderName" label="이름 (비우면 상호)" value={settings?.senderName} />
        <Field name="senderPhone" label="연락처 (비우면 대표 번호)" value={settings?.senderPhone} />
        <Field name="shipFromZipcode" label="출고지 우편번호" value={settings?.shipFromZipcode} />
        <Field name="shipFromAddress" label="출고지 주소" value={settings?.shipFromAddress} />
      </fieldset>
      <fieldset className="grid gap-3 sm:grid-cols-2">
        <legend className="mb-2 font-semibold">반품지 (상품 상세 반품 안내)</legend>
        <Field name="returnZipcode" label="반품지 우편번호" value={settings?.returnZipcode} />
        <Field name="returnAddress" label="반품지 주소" value={settings?.returnAddress} />
      </fieldset>
      {message && (
        <p role="status" className={`text-sm ${message.ok ? "text-ok" : "text-danger"}`}>
          {message.text}
        </p>
      )}
      <button type="submit" className="btn-primary self-end">
        저장
      </button>
    </form>
  );
}

function Field({ name, label, value }: { name: string; label: string; value: string | null | undefined }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="field-label">{label}</span>
      <input name={name} defaultValue={value ?? ""} className="input" />
    </label>
  );
}
