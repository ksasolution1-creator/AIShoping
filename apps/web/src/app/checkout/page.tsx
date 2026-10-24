import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { CheckoutForm } from "./CheckoutForm";

export const metadata: Metadata = { title: "주문서" };

export default function CheckoutPage() {
  return (
    <div className="flex flex-col gap-6 py-6">
      <h1 className="text-xl font-bold">주문서</h1>
      <Suspense fallback={<WhaleLoader label="주문서를 준비하는 중이에요" />}>
        <CheckoutForm />
      </Suspense>
    </div>
  );
}
