"use client";

import { useEffect, useState } from "react";

import { authApi, type SocialProviderInfo } from "@/lib/client-api";

const STYLE: Record<string, { className: string; mark: React.ReactNode }> = {
  kakao: {
    className: "bg-[#FEE500] text-black/85 hover:brightness-95",
    mark: (
      <svg viewBox="0 0 24 24" aria-hidden className="size-5">
        <path
          fill="currentColor"
          d="M12 3.5c-5.25 0-9.5 3.3-9.5 7.38 0 2.62 1.75 4.92 4.38 6.23l-.9 3.32c-.08.3.26.54.52.37l3.96-2.62c.5.06 1.01.1 1.54.1 5.25 0 9.5-3.3 9.5-7.4S17.25 3.5 12 3.5Z"
        />
      </svg>
    ),
  },
  naver: {
    className: "bg-[#03C75A] text-white hover:brightness-95",
    mark: (
      <svg viewBox="0 0 24 24" aria-hidden className="size-4">
        <path fill="currentColor" d="M15.56 12.84 8.2 2.5H2.5v19h5.94V11.16l7.36 10.34h5.7v-19h-5.94Z" />
      </svg>
    ),
  },
};

const FALLBACK: SocialProviderInfo[] = [
  { id: "kakao", label: "카카오" },
  { id: "naver", label: "네이버" },
];

/** 카카오·네이버 간편로그인. 서버에 앱 키가 없는 제공자는 '준비중'으로 보인다 */
export function SocialLoginButtons({ next }: { next: string }) {
  const [enabled, setEnabled] = useState<string[] | null>(null);

  useEffect(() => {
    authApi
      .socialProviders()
      .then((list) => setEnabled(list.map((p) => p.id)))
      .catch(() => setEnabled([]));
  }, []);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3 text-xs text-ink-faint">
        <span className="h-px flex-1 bg-line" />
        간편 로그인
        <span className="h-px flex-1 bg-line" />
      </div>
      {FALLBACK.map((p) => {
        const style = STYLE[p.id];
        const ready = enabled?.includes(p.id) ?? false;
        const label = `${p.label}로 시작하기`;
        if (!ready) {
          return (
            <span
              key={p.id}
              aria-disabled
              title={enabled === null ? undefined : "준비중입니다"}
              className={`flex h-12 items-center justify-center gap-2 rounded-md text-[15px] font-semibold opacity-45 ${style.className}`}
            >
              {style.mark}
              {label}
              {enabled !== null && <span className="text-xs font-normal">(준비중)</span>}
            </span>
          );
        }
        return (
          // 제공자 로그인 화면으로 이동해야 하므로 Link가 아닌 일반 링크
          <a
            key={p.id}
            href={`/api/auth/social/${p.id}/start?next=${encodeURIComponent(next)}`}
            className={`flex h-12 items-center justify-center gap-2 rounded-md text-[15px] font-semibold ${style.className}`}
          >
            {style.mark}
            {label}
          </a>
        );
      })}
    </div>
  );
}

/** 간편로그인 실패 사유 (서버가 /login?socialError=코드 로 돌려보낸다) */
export function socialErrorMessage(code: string | null) {
  switch (code) {
    case null:
      return null;
    case "SOCIAL_CANCELLED":
      return "간편로그인을 취소했습니다.";
    case "EMAIL_IN_USE":
      return "같은 이메일로 가입한 계정이 이미 있습니다. 이메일과 비밀번호로 로그인해 주세요.";
    case "ACCOUNT_SUSPENDED":
      return "이용이 정지된 계정입니다. 고객센터에 문의해 주세요.";
    case "SOCIAL_DISABLED":
      return "아직 준비 중인 간편로그인입니다.";
    default:
      return "간편로그인을 하지 못했습니다. 잠시 후 다시 시도해 주세요.";
  }
}
