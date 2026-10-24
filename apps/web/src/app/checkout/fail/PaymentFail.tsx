"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef } from "react";

import { orderApi } from "@/lib/client-api";

/** 결제창에서 실패·취소하고 돌아온 화면. 예약해 둔 재고를 바로 푼다 */
export function PaymentFail() {
  const params = useSearchParams();
  const orderId = params.get("orderId");
  const code = params.get("code");
  const message = params.get("message");
  const released = useRef(false);

  useEffect(() => {
    if (orderId && !released.current) {
      released.current = true;
      orderApi.abandon(orderId).catch(() => undefined);
    }
  }, [orderId]);

  const userCancelled = code === "PAY_PROCESS_CANCELED" || code === "USER_CANCEL";

  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <h1 className="text-lg font-bold">{userCancelled ? "결제를 취소했습니다" : "결제에 실패했습니다"}</h1>
      {!userCancelled && message && <p className="text-sm text-ink-soft">{message}</p>}
      <p className="text-xs text-ink-faint">주문은 취소되었고 결제된 금액은 없습니다.</p>
      <Link href="/cart" className="mt-3 h-11 rounded-md bg-brand px-5 text-sm leading-[2.75rem] font-semibold text-white">
        장바구니로 돌아가기
      </Link>
    </div>
  );
}
