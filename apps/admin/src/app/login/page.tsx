import type { Metadata } from "next";
import { Suspense } from "react";

import { LoginForm } from "./LoginForm";

export const metadata: Metadata = { title: "로그인" };

export default function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="card flex w-full max-w-sm flex-col gap-5 p-6">
        <div>
          <p className="font-bold text-brand">AIShop</p>
          <h1 className="text-lg font-bold">판매관리 로그인</h1>
        </div>
        <Suspense fallback={<div className="h-48" />}>
          <LoginForm />
        </Suspense>
      </div>
    </div>
  );
}
