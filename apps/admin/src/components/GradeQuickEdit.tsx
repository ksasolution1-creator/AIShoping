"use client";

import { useEffect, useRef, useState } from "react";

import { api, ApiError, GRADES, type GradeCode, type SkuGrade } from "@/lib/api";
import { GradePicker } from "./GradePicker";

/**
 * 상품 목록의 등급 칩. 누르면 등급 버튼이 펼쳐지고, 고르면 바로 저장한다.
 * 목록 표가 가로로 스크롤되므로 펼침 상자는 화면 기준(fixed)으로 띄워 잘리지 않게 한다.
 */
export function GradeQuickEdit({ sku, onSaved }: { sku: SkuGrade; onSaved: (grade: GradeCode) => void }) {
  const [open, setOpen] = useState<{ top: number; left: number } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => {
      if (e.type === "keydown" && (e as KeyboardEvent).key !== "Escape") return;
      if (e.type === "pointerdown" && (panelRef.current?.contains(e.target as Node) || buttonRef.current?.contains(e.target as Node))) return;
      setOpen(null);
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", close);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", close);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  function toggle() {
    if (open) {
      setOpen(null);
      return;
    }
    const rect = buttonRef.current!.getBoundingClientRect();
    const width = 208;
    setError(null);
    setOpen({ top: rect.bottom + 4, left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)) });
  }

  async function pick(grade: GradeCode) {
    if (grade === sku.grade.code) {
      setOpen(null);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api.changeSkuGrade(sku.skuId, grade);
      onSaved(grade);
      setOpen(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "바꾸지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={toggle}
        aria-expanded={open != null}
        title="눌러서 상태등급 변경"
        className="inline-flex max-w-full items-center gap-1 rounded border border-line bg-surface px-1.5 py-0.5 text-xs whitespace-nowrap hover:border-brand/60 hover:bg-surface-muted"
      >
        <span className="font-medium text-ink">{sku.grade.label}</span>
        {sku.optionText && <span className="truncate text-ink-faint">{sku.optionText}</span>}
        <span aria-hidden className="text-ink-faint">▾</span>
      </button>
      {open && (
        <div
          ref={panelRef}
          role="dialog"
          aria-label="상태등급 변경"
          style={{ top: open.top, left: open.left }}
          className="fixed z-50 flex w-52 flex-col gap-2 rounded-md border border-line bg-surface p-2 shadow-lg"
        >
          <GradePicker compact value={sku.grade.code} onChange={pick} />
          {saving && <p className="text-xs text-ink-soft">저장 중…</p>}
          {error && <p className="text-xs text-danger">{error}</p>}
        </div>
      )}
    </>
  );
}

export const gradeLabel = (code: GradeCode) => GRADES.find((g) => g.code === code)?.label ?? code;
