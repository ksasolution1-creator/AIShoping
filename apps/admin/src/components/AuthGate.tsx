"use client";

import { createContext, useContext, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ADMIN_ROLES, authApi, type Me } from "@/lib/api";

const MeContext = createContext<Me | null>(null);

export function useMe() {
  return useContext(MeContext);
}

/**
 * 운영자 로그인 확인. 실제 권한 검사는 API 서버가 하고, 여기서는 화면 이동만 처리한다.
 */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<{ me: Me | null; checked: boolean }>({ me: null, checked: false });

  useEffect(() => {
    authApi
      .me()
      .then((me) => {
        const isAdmin = me?.type === "STAFF" && me.roles.some((r) => ADMIN_ROLES.includes(r));
        if (!isAdmin) {
          const next = window.location.pathname + window.location.search;
          window.location.replace(`/login?next=${encodeURIComponent(next)}${me ? "&denied=1" : ""}`);
          return;
        }
        setState({ me, checked: true });
      })
      .catch(() => setState({ me: null, checked: true }));
  }, []);

  if (!state.checked) {
    return <WhaleLoader size="lg" label="로그인을 확인하는 중이에요" className="min-h-screen" />;
  }
  if (!state.me) {
    return (
      <p className="m-8 rounded bg-danger/10 p-4 text-sm text-danger">
        API 서버에 연결하지 못했습니다. 서버가 실행 중인지 확인한 뒤 새로고침해 주세요.
      </p>
    );
  }
  return <MeContext.Provider value={state.me}>{children}</MeContext.Provider>;
}
