"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { TextField } from "@/components/AuthCard";
import { authApi, ClientApiError, type Me } from "@/lib/client-api";

export default function MyPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authApi
      .me()
      .then((user) => {
        if (!user) router.replace("/login?next=/mypage");
        else setMe(user);
      })
      .catch(() => setMe(null));
  }, [router]);

  async function logout() {
    await authApi.logout().catch(() => undefined);
    router.replace("/");
    router.refresh();
  }

  async function withdraw(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const password = String(new FormData(event.currentTarget).get("withdraw-password") ?? "");
    try {
      await authApi.withdraw(password);
      router.replace("/");
      router.refresh();
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "탈퇴하지 못했습니다.");
    }
  }

  if (!me) return <p className="py-10 text-sm text-ink-faint">불러오는 중…</p>;

  return (
    <div className="mx-auto flex w-full max-w-lg flex-col gap-8 py-8">
      <section className="flex flex-col gap-1">
        <h1 className="text-xl font-bold">{me.name}님</h1>
        <p className="text-sm text-ink-soft">{me.email}</p>
      </section>

      <section className="flex flex-col divide-y divide-line rounded-md border border-line">
        <Link href="/orders" className="flex items-center justify-between px-4 py-3.5 text-sm hover:bg-surface-muted">
          주문·배송 조회
          <span className="text-ink-faint">›</span>
        </Link>
        {["취소·반품 내역", "배송지 관리"].map((item) => (
          <div key={item} className="flex items-center justify-between px-4 py-3.5 text-sm">
            {item}
            <span className="text-xs text-ink-faint">준비중</span>
          </div>
        ))}
      </section>

      <button type="button" onClick={logout} className="h-11 rounded-md border border-line text-sm hover:bg-surface-muted">
        로그아웃
      </button>

      <section className="flex flex-col gap-3 border-t border-line pt-6">
        {!withdrawOpen ? (
          <button
            type="button"
            onClick={() => setWithdrawOpen(true)}
            className="self-start text-sm text-ink-faint underline underline-offset-4"
          >
            회원 탈퇴
          </button>
        ) : (
          <form onSubmit={withdraw} className="flex flex-col gap-3 rounded-md bg-surface-muted p-4">
            <h2 className="font-semibold">회원 탈퇴</h2>
            <p className="text-sm leading-relaxed text-ink-soft">
              탈퇴하면 이름, 이메일, 배송지 등 개인정보가 바로 삭제되고 되돌릴 수 없습니다. 주문 기록은
              전자상거래법에 따라 5년간 보관됩니다.
            </p>
            <TextField
              id="withdraw-password"
              label="비밀번호 확인"
              type="password"
              autoComplete="current-password"
              required
              error={error ?? undefined}
            />
            <div className="flex gap-2">
              <button type="button" onClick={() => setWithdrawOpen(false)} className="h-10 flex-1 rounded-md border border-line bg-surface text-sm">
                취소
              </button>
              <button type="submit" className="h-10 flex-1 rounded-md bg-sale text-sm font-semibold text-white">
                탈퇴하기
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
