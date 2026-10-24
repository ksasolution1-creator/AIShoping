"use client";

import { useEffect, useRef, useState } from "react";

// 다음(카카오) 우편번호 서비스: 무료, 키 없음. 앱 WebView에서도 열리도록 팝업 대신 레이어에 넣는다.
const SCRIPT_URL = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

type PostcodeResult = {
  zonecode: string;
  roadAddress: string;
  jibunAddress: string;
  buildingName: string;
  apartment: "Y" | "N";
};

type DaumPostcode = new (options: {
  oncomplete: (data: PostcodeResult) => void;
  width: string;
  height: string;
}) => { embed: (element: HTMLElement) => void };

declare global {
  interface Window {
    daum?: { Postcode: DaumPostcode };
  }
}

function loadScript(): Promise<void> {
  if (window.daum?.Postcode) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_URL}"]`);
    const script = existing ?? document.createElement("script");
    script.addEventListener("load", () => resolve());
    script.addEventListener("error", () => reject(new Error("우편번호 서비스를 불러오지 못했습니다.")));
    if (!existing) {
      script.src = SCRIPT_URL;
      script.async = true;
      document.head.appendChild(script);
    }
  });
}

export function AddressSearch({ onSelect }: { onSelect: (zipcode: string, address: string) => void }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const layerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    loadScript()
      .then(() => {
        if (cancelled || !layerRef.current || !window.daum) return;
        new window.daum.Postcode({
          width: "100%",
          height: "100%",
          oncomplete: (data) => {
            const base = data.roadAddress || data.jibunAddress;
            const extra = data.buildingName && data.apartment === "Y" ? ` (${data.buildingName})` : "";
            onSelect(data.zonecode, base + extra);
            setOpen(false);
          },
        }).embed(layerRef.current);
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [open, onSelect]);

  return (
    <>
      <button
        type="button"
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
        className="h-11 shrink-0 rounded-xl border-2 border-brand px-4 text-sm font-semibold text-brand hover:bg-brand/5"
      >
        주소 찾기
      </button>
      {error && <p className="text-xs text-sale">{error} 직접 입력해 주세요.</p>}
      {open && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="주소 검색"
          className="fixed inset-0 z-50 flex items-end justify-center bg-navy/50 sm:items-center"
          onClick={(e) => e.target === e.currentTarget && setOpen(false)}
        >
          <div className="flex h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-t-2xl bg-surface sm:h-[34rem] sm:rounded-2xl">
            <div className="flex items-center justify-between border-b border-line px-4 py-3">
              <span className="text-lg font-bold text-navy">주소 검색</span>
              <button type="button" onClick={() => setOpen(false)} aria-label="닫기" className="text-xl text-ink-faint">
                ×
              </button>
            </div>
            <div ref={layerRef} className="flex-1" />
          </div>
        </div>
      )}
    </>
  );
}
