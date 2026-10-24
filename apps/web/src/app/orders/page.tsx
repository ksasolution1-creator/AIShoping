"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ProductImage } from "@/components/ProductImage";
import { ClientApiError, ITEM_STATUS_LABEL, loginUrl, orderApi, type OrderSummary, type Page } from "@/lib/client-api";
import { formatWon } from "@/lib/format";

export default function OrdersPage() {
  const router = useRouter();
  const [data, setData] = useState<Page<OrderSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    orderApi
      .list()
      .then(setData)
      .catch((e) => {
        if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
        else setError("주문 내역을 불러오지 못했습니다.");
      });
  }, [router]);

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5 py-6">
      <h1 className="text-xl font-bold">주문·배송 조회</h1>
      {!data && <p className="text-sm text-ink-faint">{error ?? "불러오는 중…"}</p>}
      {data?.items.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <p className="font-medium">주문 내역이 없습니다.</p>
          <Link href="/" className="text-sm text-brand underline underline-offset-4">
            상품 보러 가기
          </Link>
        </div>
      )}
      <ul className="flex flex-col gap-3">
        {data?.items.map((order) => (
          <li key={order.orderNo}>
            <Link
              href={`/orders/${order.orderNo}`}
              className="flex flex-col gap-3 rounded-md border border-line p-4 hover:border-ink-faint"
            >
              <div className="flex items-center justify-between text-xs text-ink-faint">
                <span>{new Date(order.orderedAt).toLocaleDateString("ko-KR")} 주문</span>
                <span className="font-mono">{order.orderNo}</span>
              </div>
              <div className="flex gap-3">
                <ProductImage src={order.thumbnailUrl} alt="" className="size-16 rounded-md" />
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-sm font-semibold">
                    {order.itemStatuses.map((s) => ITEM_STATUS_LABEL[s] ?? s).join(" · ")}
                  </span>
                  <span className="line-clamp-1 text-sm">{order.orderName}</span>
                  <span className="text-sm font-bold tabular-nums">{formatWon(order.payAmount)}</span>
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
