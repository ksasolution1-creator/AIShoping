import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { PaymentSuccess } from "./PaymentSuccess";

export const metadata: Metadata = { title: "결제 확인" };

export default function SuccessPage() {
  return (
    <Suspense fallback={<WhaleLoader size="lg" label="결제를 확인하고 있어요" />}>
      <PaymentSuccess />
    </Suspense>
  );
}
