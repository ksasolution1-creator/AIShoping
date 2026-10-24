import type { Metadata } from "next";
import { Suspense } from "react";

import { OrderList } from "./OrderList";

export const metadata: Metadata = { title: "주문 관리" };

export default function OrdersPage() {
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-xl font-bold">주문 관리</h1>
      <Suspense fallback={<p className="text-sm text-ink-faint">불러오는 중…</p>}>
        <OrderList />
      </Suspense>
    </div>
  );
}
