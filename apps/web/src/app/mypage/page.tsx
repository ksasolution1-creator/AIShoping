"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { TextField } from "@/components/AuthCard";
import { authApi, ClientApiError, type LoginMethods, type Me } from "@/lib/client-api";
import { PasswordChange } from "./PasswordChange";

const SOCIAL_LABEL: Record<string, string> = { KAKAO: "카카오", NAVER: "네이버" };

export default function MyPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [methods, setMethods] = useState<LoginMethods | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authApi
      .me()
      .then((user) => {
        if (!user) {
          router.replace("/login?next=/mypage");
          return;
        }
        setMe(user);
        return authApi.loginMethods().then(setMethods);
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
    if (!hasPassword && !new FormData(event.currentTarget).get("withdraw-confirm")) {
      setError("안내를 확인하고 체크해 주세요.");
      return;
    }
    try {
      await authApi.withdraw(password);
      router.replace("/");
      router.refresh();
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "탈퇴하지 못했습니다.");
    }
  }

  if (!me) return <WhaleLoader />;
  const hasPassword = methods?.hasPassword !== false;

  return (
    <div className="mx-auto flex w-full max-w-lg flex-col gap-8 py-8">
      <section className="flex flex-col gap-1">
        <h1 className="text-xl font-bold">{me.name}님</h1>
        {me.email && <p className="text-sm text-ink-soft">{me.email}</p>}
        {methods && methods.socialProviders.length > 0 && (
          <p className="text-xs text-ink-faint">
            {methods.socialProviders.map((p) => SOCIAL_LABEL[p] ?? p).join(", ")} 간편로그인 연결됨
          </p>
        )}
      </section>

      <section className="flex flex-col divide-y divide-line rounded-md border border-line">
        <Link href="/orders" className="flex items-center justify-between px-4 py-3.5 text-sm hover:bg-surface-muted">
          주문·배송 조회
          <span className="text-ink-faint">›</span>
        </Link>
        <Link href="/wishlist" className="flex items-center justify-between px-4 py-3.5 text-sm hover:bg-surface-muted">
          찜한 상품
          <span className="text-ink-faint">›</span>
        </Link>
        <Link href="/business" className="flex items-center justify-between px-4 py-3.5 text-sm hover:bg-surface-muted">
          사업자 회원 인증
          <span className="text-ink-faint">›</span>
        </Link>
        {["취소·반품 내역", "배송지 관리"].map((item) => (
          <div key={item} className="flex items-center justify-between px-4 py-3.5 text-sm">
            {item}
            <span className="text-xs text-ink-faint">준비중</span>
          </div>
        ))}
        {methods?.hasPassword !== false && <PasswordChange email={me.email} />}
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
            {hasPassword ? (
              <TextField
                id="withdraw-password"
                label="비밀번호 확인"
                type="password"
                autoComplete="current-password"
                required
                error={error ?? undefined}
              />
            ) : (
              <>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" name="withdraw-confirm" className="size-4 accent-sale" />
                  위 안내를 확인했고 탈퇴합니다.
                </label>
                {error && <p className="text-xs text-sale">{error}</p>}
              </>
            )}
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
