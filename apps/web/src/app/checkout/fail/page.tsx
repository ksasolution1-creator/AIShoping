import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { PaymentFail } from "./PaymentFail";

export const metadata: Metadata = { title: "결제 실패" };

export default function FailPage() {
  return (
    <Suspense fallback={<WhaleLoader />}>
      <PaymentFail />
    </Suspense>
  );
}
