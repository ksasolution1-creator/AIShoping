import Link from "next/link";

const DOCS = [
  { href: "/terms", label: "이용약관" },
  { href: "/privacy", label: "개인정보처리방침" },
  { href: "/policy", label: "배송·교환·환불 안내" },
];

/** 이용약관·개인정보처리방침·이용안내 공통 틀 */
export function PolicyDocument({
  title,
  current,
  effectiveDate,
  children,
}: {
  title: string;
  current: string;
  effectiveDate: string;
  children: React.ReactNode;
}) {
  return (
    <article className="mx-auto flex w-full max-w-3xl flex-col gap-6 py-8">
      <nav className="flex flex-wrap gap-2 text-sm">
        {DOCS.map((d) => (
          <Link
            key={d.href}
            href={d.href}
            aria-current={d.href === current ? "page" : undefined}
            className={`rounded-full px-3 py-1 ${
              d.href === current ? "bg-navy font-semibold text-white" : "bg-surface-muted text-ink-soft hover:text-ink"
            }`}
          >
            {d.label}
          </Link>
        ))}
      </nav>
      <header className="flex flex-col gap-1 border-b border-ink pb-4">
        <h1 className="text-2xl font-bold text-navy">{title}</h1>
        <p className="text-sm text-ink-faint">시행일: {effectiveDate}</p>
      </header>
      <div className="flex flex-col gap-6 text-sm leading-relaxed text-ink-soft">{children}</div>
    </article>
  );
}

/** 조항 하나 (제목 + 본문) */
export function Clause({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-base font-bold text-ink">{title}</h2>
      {children}
    </section>
  );
}

/** 1. 2. 3. 번호 목록 */
export function Numbered({ children }: { children: React.ReactNode }) {
  return <ol className="flex list-decimal flex-col gap-1 pl-6">{children}</ol>;
}

export function Bullets({ children }: { children: React.ReactNode }) {
  return <ul className="flex list-disc flex-col gap-1 pl-6">{children}</ul>;
}

/** 표 (항목 | 내용 ...) */
export function PolicyTable({ head, rows }: { head: string[]; rows: React.ReactNode[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[32rem] border-collapse text-left text-xs">
        <thead>
          <tr className="bg-surface-muted">
            {head.map((h) => (
              <th key={h} className="border border-line px-3 py-2 font-semibold text-ink">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i}>
              {row.map((cell, j) => (
                <td key={j} className="border border-line px-3 py-2 align-top">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
