"use client";

import { useEffect, useState } from "react";

import { authApi, ClientApiError, type MarketingConsent as Consent } from "@/lib/client-api";

/** 마케팅 수신 동의 변경. 바꾸면 처리 결과(일시)를 바로 보여준다 (정보통신망법 수신동의 처리결과 안내) */
export function MarketingConsent() {
  const [consent, setConsent] = useState<Consent | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authApi.marketing().then(setConsent).catch(() => setConsent(null));
  }, []);

  async function change(agreed: boolean) {
    setBusy(true);
    setResult(null);
    try {
      const next = await authApi.changeMarketing(agreed);
      setConsent(next);
      const when = new Date().toLocaleString("ko-KR");
      setResult(`${when} 떨고래 마켓의 할인·이벤트 소식 수신을 ${agreed ? "동의" : "거부"}하셨습니다.`);
    } catch (e) {
      setResult(e instanceof ClientApiError ? e.message : "변경하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (!consent) return null;

  return (
    <div className="flex flex-col gap-2 px-4 py-3.5 text-sm">
      <label className="flex items-center justify-between gap-3">
        <span className="flex flex-col">
          할인·이벤트 소식 받기
          <span className="text-xs text-ink-faint">
            {consent.agreed && consent.agreedAt
              ? `${new Date(consent.agreedAt).toLocaleDateString("ko-KR")} 동의`
              : "받지 않음"}
          </span>
        </span>
        <input
          type="checkbox"
          role="switch"
          checked={consent.agreed}
          disabled={busy}
          onChange={(e) => change(e.target.checked)}
          className="size-5 accent-brand"
        />
      </label>
      {result && (
        <p role="status" className="text-xs text-brand">
          {result}
        </p>
      )}
    </div>
  );
}
