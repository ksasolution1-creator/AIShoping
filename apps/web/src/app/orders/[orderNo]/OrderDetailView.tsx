"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ProductImage } from "@/components/ProductImage";
import { ClientApiError, ITEM_STATUS_LABEL, loginUrl, orderApi, type OrderDetail } from "@/lib/client-api";
import { formatWon } from "@/lib/format";

export function OrderDetailView({ orderNo }: { orderNo: string }) {
  const router = useRouter();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    orderApi
      .detail(orderNo)
      .then(setOrder)
      .catch((e) => {
        if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
        else setError(e instanceof ClientApiError ? e.message : "주문을 불러오지 못했습니다.");
      });
  }, [orderNo, router]);

  async function cancel() {
    setBusy(true);
    try {
      setOrder(await orderApi.cancel(orderNo, "고객 요청 취소"));
      setConfirmingCancel(false);
      setError(null);
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "취소하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (!order) return <p className="py-10 text-sm text-ink-faint">{error ?? "불러오는 중…"}</p>;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 py-6">
      <div className="flex flex-col gap-1">
        <Link href="/orders" className="text-sm text-ink-faint hover:text-ink-soft">
          ← 주문 내역
        </Link>
        <h1 className="text-xl font-bold">주문 상세</h1>
        <p className="text-sm text-ink-faint">
          {new Date(order.orderedAt).toLocaleString("ko-KR")} · <span className="font-mono">{order.orderNo}</span>
        </p>
      </div>

      <section className="flex flex-col divide-y divide-line border-y border-line">
        {order.items.map((item) => (
          <div key={item.id} className="flex gap-3 py-3">
            <ProductImage src={item.thumbnailUrl} alt={item.productName} className="size-16 rounded-md" />
            <div className="flex min-w-0 flex-1 flex-col gap-0.5 text-sm">
              <span className="font-semibold text-brand">{ITEM_STATUS_LABEL[item.status] ?? item.status}</span>
              <span className="line-clamp-1">{item.productName}</span>
              <span className="text-xs text-ink-faint">
                {item.optionText && `${item.optionText} · `}
                {item.qty}개
              </span>
            </div>
            <span className="text-sm font-bold tabular-nums">{formatWon(item.lineTotal)}</span>
          </div>
        ))}
      </section>

      {order.shipments.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="font-bold">배송 정보</h2>
          {order.shipments.map((s) => (
            <p key={s.id} className="rounded-md bg-surface-muted p-3 text-sm">
              {s.carrierName} <span className="font-mono">{s.trackingNo}</span>
            </p>
          ))}
        </section>
      )}

      <section className="flex flex-col gap-2 text-sm">
        <h2 className="font-bold">배송지</h2>
        <p>
          {order.receiver.name} · {order.receiver.phone}
        </p>
        <p className="text-ink-soft">
          ({order.receiver.zipcode}) {order.receiver.address1} {order.receiver.address2}
        </p>
        {order.receiver.deliveryMemo && <p className="text-ink-faint">요청사항: {order.receiver.deliveryMemo}</p>}
      </section>

      <section className="flex flex-col gap-2 rounded-md bg-surface-muted p-4 text-sm">
        <div className="flex justify-between">
          <span className="text-ink-soft">상품 금액</span>
          <span className="tabular-nums">{formatWon(order.itemsTotal)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-ink-soft">배송비</span>
          <span className="tabular-nums">{order.shippingFee === 0 ? "무료" : formatWon(order.shippingFee)}</span>
        </div>
        <div className="flex justify-between border-t border-line pt-2 font-bold">
          <span>결제 금액</span>
          <span className="tabular-nums">{formatWon(order.payAmount)}</span>
        </div>
        {order.payment && (
          <p className="text-xs text-ink-faint">
            {order.payment.easyPayProvider ?? order.payment.method}
            {order.payment.status === "CANCELED" && " · 전액 환불됨"}
          </p>
        )}
      </section>

      {error && (
        <p role="alert" className="rounded-md bg-sale/10 p-3 text-sm text-sale">
          {error}
        </p>
      )}

      {order.cancellable &&
        (confirmingCancel ? (
          <div className="flex flex-col gap-3 rounded-md border border-sale/30 p-4">
            <p className="text-sm">주문을 취소하면 결제 금액 {formatWon(order.payAmount)}이 환불됩니다. 취소할까요?</p>
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setConfirmingCancel(false)} className="h-11 rounded-md border border-line text-sm">
                그대로 두기
              </button>
              <button
                type="button"
                onClick={cancel}
                disabled={busy}
                className="h-11 rounded-md bg-sale text-sm font-semibold text-white disabled:opacity-50"
              >
                {busy ? "취소 중…" : "주문 취소"}
              </button>
            </div>
          </div>
        ) : (
          <button type="button" onClick={() => setConfirmingCancel(true)} className="h-11 rounded-md border border-line text-sm">
            주문 취소
          </button>
        ))}
    </div>
  );
}
