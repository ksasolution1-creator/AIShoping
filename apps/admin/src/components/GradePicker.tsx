"use client";

import { GRADES, type GradeCode } from "@/lib/api";

/** 상태등급을 버튼 한 번으로 고른다 (드롭다운 대신). 등록·수정 화면에서 같이 쓴다 */
export function GradePicker({
  value,
  onChange,
  id,
  compact = false,
}: {
  value: GradeCode | "";
  onChange: (grade: GradeCode) => void;
  id?: string;
  /** 표 안처럼 좁은 곳: 2열로 작게 */
  compact?: boolean;
}) {
  return (
    <div
      id={id}
      role="radiogroup"
      aria-label="상태등급"
      className={compact ? "grid grid-cols-2 gap-1" : "flex flex-wrap gap-1.5"}
    >
      {GRADES.map((g) => {
        const selected = value === g.code;
        return (
          <button
            key={g.code}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(g.code)}
            className={`rounded border whitespace-nowrap transition-colors ${
              compact ? "px-1.5 py-1 text-xs" : "px-2.5 py-1.5 text-sm"
            } ${
              selected
                ? "border-brand bg-brand font-semibold text-white"
                : "border-line bg-surface text-ink-soft hover:border-brand/50 hover:bg-surface-muted"
            }`}
          >
            {g.label}
          </button>
        );
      })}
    </div>
  );
}
