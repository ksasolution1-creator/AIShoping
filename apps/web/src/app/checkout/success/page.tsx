import type { Metadata } from "next";
import { Suspense } from "react";

import { PaymentSuccess } from "./PaymentSuccess";

export const metadata: Metadata = { title: "결제 확인" };

export default function SuccessPage() {
  return (
    <Suspense fallback={<p className="py-10 text-sm text-ink-faint">결제를 확인하고 있습니다…</p>}>
      <PaymentSuccess />
    </Suspense>
  );
}
