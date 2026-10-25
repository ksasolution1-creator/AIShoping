"use client";

import { useState } from "react";

/**
 * 삭제처럼 되돌리기 어려운 동작. 한 번 누르면 그 자리에서 "정말 ~할까요?"를 묻고, 한 번 더 눌러야 실행한다
 * (브라우저 확인창 대신 화면 안에서 묻는다).
 */
export function ConfirmButton({
  label = "삭제",
  confirmLabel = "삭제",
  question = "정말 삭제할까요?",
  onConfirm,
  className = "btn-outline text-danger",
  disabled,
}: {
  label?: React.ReactNode;
  confirmLabel?: string;
  question?: string;
  onConfirm: () => Promise<void> | void;
  className?: string;
  disabled?: boolean;
}) {
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);

  if (!asking) {
    return (
      <button type="button" className={className} disabled={disabled} onClick={() => setAsking(true)}>
        {label}
      </button>
    );
  }

  return (
    <span className="inline-flex flex-wrap items-center gap-1.5 text-sm">
      <span className="text-danger">{question}</span>
      <button
        type="button"
        className="btn-primary bg-danger hover:bg-danger/90"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await onConfirm();
          } finally {
            setBusy(false);
            setAsking(false);
          }
        }}
      >
        {busy ? "처리 중…" : confirmLabel}
      </button>
      <button type="button" className="btn-outline" disabled={busy} onClick={() => setAsking(false)}>
        취소
      </button>
    </span>
  );
}
