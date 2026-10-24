"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { ClientApiError, orderApi, type OrderDetail } from "@/lib/client-api";
import { formatWon } from "@/lib/format";

/** 결제창 인증이 끝나고 돌아온 화면. 서버가 승인 API를 호출해야 결제가 끝난다 */
export function PaymentSuccess() {
  const params = useSearchParams();
  const paymentKey = params.get("paymentKey");
  const orderId = params.get("orderId");
  const amount = Number(params.get("amount"));
  const validParams = Boolean(paymentKey && orderId && amount);

  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [requestError, setError] = useState<{ message: string; pending: boolean } | null>(null);
  const requested = useRef(false);
  const error = validParams ? requestError : { message: "결제 정보가 올바르지 않습니다.", pending: false };

  useEffect(() => {
    // 승인 요청은 한 번만 보낸다 (개발 모드에서 effect가 두 번 실행되는 것 방지)
    if (requested.current || !paymentKey || !orderId || !amount) return;
    requested.current = true;
    orderApi
      .confirm(paymentKey, orderId, amount)
      .then(setOrder)
      .catch((e) =>
        setError({
          message: e instanceof ClientApiError ? e.message : "결제를 확인하지 못했습니다.",
          pending: e instanceof ClientApiError && e.code === "PAYMENT_PENDING",
        }),
      );
  }, [paymentKey, orderId, amount]);

  if (error) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
        <h1 className="text-lg font-bold">{error.pending ? "결제 결과를 확인 중입니다" : "결제가 완료되지 않았습니다"}</h1>
        <p className="text-sm text-ink-soft">{error.message}</p>
        <div className="mt-2 flex gap-2">
          <Link href="/orders" className="h-11 rounded-md border border-line px-4 text-sm leading-[2.75rem]">
            주문 내역
          </Link>
          <Link href="/cart" className="h-11 rounded-md bg-brand px-4 text-sm leading-[2.75rem] font-semibold text-white">
            장바구니로
          </Link>
        </div>
      </div>
    );
  }

  if (!order) {
    return <p className="py-16 text-center text-sm text-ink-faint">결제를 확인하고 있습니다. 창을 닫지 마세요…</p>;
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 py-12">
      <div className="flex flex-col items-center gap-2 text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-brand/10 text-2xl text-brand">✓</span>
        <h1 className="text-xl font-bold">주문이 완료되었습니다</h1>
        <p className="text-sm text-ink-soft">주문번호 {order.orderNo}</p>
      </div>
      <dl className="flex flex-col gap-2 rounded-md bg-surface-muted p-5 text-sm">
        <div className="flex justify-between">
          <dt className="text-ink-soft">결제 금액</dt>
          <dd className="font-bold tabular-nums">{formatWon(order.payAmount)}</dd>
        </div>
        {order.payment && (
          <div className="flex justify-between">
            <dt className="text-ink-soft">결제 수단</dt>
            <dd>{order.payment.easyPayProvider ?? order.payment.method}</dd>
          </div>
        )}
        <div className="flex justify-between">
          <dt className="text-ink-soft">받는 분</dt>
          <dd>{order.receiver.name}</dd>
        </div>
      </dl>
      <div className="grid grid-cols-2 gap-2">
        <Link href="/" className="h-12 rounded-md border border-line text-center leading-[3rem]">
          쇼핑 계속하기
        </Link>
        <Link
          href={`/orders/${order.orderNo}`}
          className="h-12 rounded-md bg-brand text-center leading-[3rem] font-semibold text-white"
        >
          주문 상세 보기
        </Link>
      </div>
    </div>
  );
}
