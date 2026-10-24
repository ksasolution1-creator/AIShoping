"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const MENU = [
  { href: "/orders", label: "주문 관리", ready: true },
  { href: "/products", label: "상품 관리", ready: true },
  { href: "/categories", label: "카테고리", ready: true },
  { href: "#inventory", label: "재고·매입", ready: false },
  { href: "#shipping", label: "배송·송장", ready: false },
];

export function ActiveNavLinks() {
  return <NavLinks pathname={usePathname()} />;
}

export function NavLinks({ pathname }: { pathname: string }) {
  return (
    <ul className="flex gap-1 md:flex-col">
      {MENU.map((item) => {
        const active = item.ready && pathname.startsWith(item.href);
        return (
          <li key={item.href}>
            {item.ready ? (
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`block rounded px-3 py-2 text-sm whitespace-nowrap ${
                  active ? "bg-surface-muted font-semibold text-ink" : "text-ink-soft hover:bg-surface-muted"
                }`}
              >
                {item.label}
              </Link>
            ) : (
              <span className="block px-3 py-2 text-sm whitespace-nowrap text-ink-faint" title="준비중">
                {item.label} <span className="text-[10px]">준비중</span>
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}
