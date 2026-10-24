import Link from "next/link";

const TABS = [
  { href: "/inventory", label: "재고 현황" },
  { href: "/purchases", label: "매입" },
  { href: "/suppliers", label: "매입처" },
];

/** 재고·매입 화면 위쪽 탭 */
export function InventoryTabs({ current }: { current: "/inventory" | "/purchases" | "/suppliers" }) {
  return (
    <nav className="flex gap-1 border-b border-line" aria-label="재고·매입">
      {TABS.map((tab) => (
        <Link
          key={tab.href}
          href={tab.href}
          aria-current={current === tab.href ? "page" : undefined}
          className={`-mb-px border-b-2 px-4 py-2 text-sm ${
            current === tab.href ? "border-brand font-semibold text-ink" : "border-transparent text-ink-soft hover:text-ink"
          }`}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}
