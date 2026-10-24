import type { Metadata } from "next";
import { Suspense } from "react";

import { CheckoutForm } from "./CheckoutForm";

export const metadata: Metadata = { title: "주문서" };

export default function CheckoutPage() {
  return (
    <div className="flex flex-col gap-6 py-6">
      <h1 className="text-xl font-bold">주문서</h1>
      <Suspense fallback={<p className="text-sm text-ink-faint">불러오는 중…</p>}>
        <CheckoutForm />
      </Suspense>
    </div>
  );
}
