import type { Grade, GradeCode } from "@/lib/api";

// 상태등급마다 색을 고정해 목록에서 한눈에 구분되게 한다
const styles: Record<GradeCode, string> = {
  NEW: "bg-grade-new/10 text-grade-new ring-grade-new/25",
  RETURN_UNOPENED: "bg-grade-return/10 text-grade-return ring-grade-return/25",
  RETURN_OPENED: "bg-grade-opened/10 text-grade-opened ring-grade-opened/25",
  REFURB: "bg-grade-refurb/10 text-grade-refurb ring-grade-refurb/25",
  USED_A: "bg-grade-used/10 text-grade-used ring-grade-used/25",
  USED_B: "bg-grade-used/10 text-grade-used ring-grade-used/25",
  USED_C: "bg-grade-used/10 text-grade-used ring-grade-used/25",
  DISPLAY: "bg-grade-display/10 text-grade-display ring-grade-display/25",
};

export function GradeBadge({
  grade,
  size = "sm",
}: {
  grade: Grade;
  size?: "sm" | "md";
}) {
  const sizing =
    size === "md" ? "px-2.5 py-1 text-sm" : "px-1.5 py-0.5 text-[11px]";
  return (
    <span
      className={`inline-flex items-center rounded font-medium whitespace-nowrap ring-1 ring-inset ${sizing} ${styles[grade.code]}`}
    >
      {grade.label}
    </span>
  );
}
