"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { useMe } from "./AuthGate";

const MENU = [
  { href: "/orders", label: "주문 관리", ready: true },
  { href: "/claims", label: "취소·반품", ready: true },
  { href: "/products", label: "상품 관리", ready: true },
  { href: "/categories", label: "카테고리", ready: true },
  { href: "/community", label: "문의·후기", ready: true },
  { href: "/members", label: "회원 관리", ready: true },
  { href: "/business-members", label: "사업자 회원", ready: true },
  { href: "/inventory", label: "재고·매입", ready: true, also: ["/purchases", "/suppliers"] },
  { href: "/shipping", label: "배송·송장", ready: true },
  { href: "/staff", label: "운영자 계정", ready: true, adminOnly: true },
];

export function ActiveNavLinks() {
  return <NavLinks pathname={usePathname()} />;
}

export function NavLinks({ pathname }: { pathname: string }) {
  const isAdmin = useMe()?.roles.includes("ADMIN") ?? false;
  return (
    <ul className="flex gap-1 md:flex-col">
      {MENU.filter((item) => !("adminOnly" in item) || isAdmin).map((item) => {
        const prefixes = [item.href, ...(("also" in item && item.also) || [])];
        const active = item.ready && prefixes.some((p) => pathname.startsWith(p));
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
