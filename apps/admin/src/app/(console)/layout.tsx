import { AdminNav } from "@/components/AdminNav";
import { AuthGate } from "@/components/AuthGate";

// 로그인 확인이 끝나야 화면을 그리므로 Next.js의 즉시 이동(instant navigation) 검증 대상에서 뺀다.
// 관리자 화면은 검색 노출·첫 화면 속도보다 권한 확인이 우선이다.
export const instant = false;

/** 로그인한 운영자만 보는 화면 */
export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGate>
      <div className="min-h-full md:flex">
        <AdminNav />
        <main className="min-w-0 flex-1 px-4 py-6 md:px-8">{children}</main>
      </div>
    </AuthGate>
  );
}
