import type { Metadata } from "next";
import { Suspense } from "react";

import { PaymentFail } from "./PaymentFail";

export const metadata: Metadata = { title: "결제 실패" };

export default function FailPage() {
  return (
    <Suspense fallback={null}>
      <PaymentFail />
    </Suspense>
  );
}
