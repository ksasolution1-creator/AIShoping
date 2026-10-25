"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { Thumb } from "@/components/Thumb";
import {
  ApiError,
  claimAdminApi,
  formatDate,
  formatWon,
  gradeLabel,
  ITEM_STATUS_LABEL,
  orderAdminApi,
  type Carrier,
  type OrderDetail,
} from "@/lib/api";

export function AdminOrderDetail({ orderNo }: { orderNo: string }) {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);

  useEffect(() => {
    orderAdminApi
      .detail(orderNo)
      .then(setOrder)
      .catch((e) => setNotice({ kind: "error", text: e instanceof ApiError ? e.message : "주문을 불러오지 못했습니다." }));
    orderAdminApi.carriers().then(setCarriers).catch(() => setCarriers([]));
  }, [orderNo]);

  async function run(action: () => Promise<OrderDetail>, done: string) {
    setBusy(true);
    try {
      setOrder(await action());
      setNotice({ kind: "ok", text: done });
      return true;
    } catch (e) {
      setNotice({ kind: "error", text: e instanceof ApiError ? e.message : "처리하지 못했습니다." });
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (!order) {
    return notice ? <p className="text-sm text-danger">{notice.text}</p> : <WhaleLoader label="주문을 불러오는 중이에요" />;
  }

  const statuses = new Set(order.items.map((i) => i.status));
  const canAccept = statuses.has("PAID");
  const canShip = statuses.has("PAID") || statuses.has("PREPARING");
  const awaitingDeposit = order.status === "PENDING_PAYMENT" && order.bankDeposit !== null;
  const pendingRefunds = order.refunds.filter((r) => r.status === "REQUESTED");

  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Link href="/orders" className="text-sm text-ink-faint hover:text-ink-soft">
          ← 주문 목록
        </Link>
        <div className="flex flex-wrap items-baseline gap-3">
          <h1 className="font-mono text-xl font-bold">{order.orderNo}</h1>
          <span className="text-sm text-ink-faint">
            {formatDate(order.orderedAt)} 주문 · {order.ordererName}
            {order.ordererEmail && ` (${order.ordererEmail})`}
          </span>
        </div>
        {notice && (
          <p
            role="status"
            className={`rounded p-3 text-sm ${notice.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
          >
            {notice.text}
          </p>
        )}
      </div>

      {/* 무통장입금: 통장에 입금된 것을 확인한 뒤 결제완료로 바꾼다 */}
      {awaitingDeposit && order.bankDeposit && (
        <section className="card flex flex-wrap items-center justify-between gap-3 border-warn/40 p-4 text-sm">
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold">입금 확인 대기</span>
            <span>
              입금자명 <strong>{order.bankDeposit.depositorName}</strong> · 금액{" "}
              <strong className="tabular-nums">{formatWon(order.payAmount)}</strong>
            </span>
            <span className="text-xs text-ink-faint">
              {order.bankDeposit.bankName} {order.bankDeposit.accountNo}
              {order.bankDeposit.dueAt && ` · ${formatDate(order.bankDeposit.dueAt)}까지 입금 없으면 자동 취소`}
            </span>
          </div>
          <button
            type="button"
            className="btn-primary"
            disabled={busy}
            onClick={() => run(() => orderAdminApi.confirmDeposit(orderNo), "입금을 확인했습니다. 발주확인 후 상품을 준비해 주세요.")}
          >
            입금 확인
          </button>
        </section>
      )}

      {/* 무통장입금 환불: 고객 계좌로 직접 보낸 뒤 송금 완료로 표시 */}
      {pendingRefunds.length > 0 && (
        <section className="card flex flex-col gap-2 border-danger/30 p-4 text-sm">
          <h2 className="font-semibold text-danger">환불 송금 필요</h2>
          {order.refundAccount ? (
            <p className="rounded bg-surface-muted p-3">
              환불 계좌: <strong>{order.refundAccount.bankName}</strong>{" "}
              <span className="font-mono tabular-nums">{order.refundAccount.accountNo}</span> (예금주{" "}
              {order.refundAccount.holder})
            </p>
          ) : (
            <p className="text-xs text-danger">
              고객이 입력한 환불 계좌가 없습니다. 고객({order.receiver.phone})에게 계좌를 확인해 주세요.
            </p>
          )}
          <p className="text-xs text-ink-faint">위 계좌로 아래 금액을 보낸 뒤 송금 완료를 눌러 주세요.</p>
          {pendingRefunds.map((r) => (
            <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded bg-surface-muted p-3">
              <span>
                <strong className="tabular-nums">{formatWon(r.amount)}</strong>
                <span className="text-ink-faint"> · {r.reason} · {formatDate(r.createdAt)}</span>
              </span>
              <button
                type="button"
                className="btn-outline"
                disabled={busy}
                onClick={() => run(() => orderAdminApi.completeRefund(orderNo, r.id), "환불 송금 완료로 표시했습니다.")}
              >
                송금 완료
              </button>
            </div>
          ))}
        </section>
      )}

      {/* 처리 단계: 발주확인 → 송장 등록 */}
      {(canAccept || canShip) && (
        <section className="card flex flex-wrap items-end gap-3 p-4">
          {canAccept && (
            <button
              type="button"
              className="btn-outline"
              disabled={busy}
              onClick={() => run(() => orderAdminApi.accept(orderNo), "발주확인했습니다. 상품을 준비해 주세요.")}
            >
              발주확인
            </button>
          )}
          {canShip && (
            <form
              className="flex flex-wrap items-end gap-2"
              onSubmit={async (e) => {
                e.preventDefault();
                const form = new FormData(e.currentTarget);
                await run(
                  () =>
                    orderAdminApi.ship(orderNo, Number(form.get("carrierId")), String(form.get("trackingNo") ?? "").trim()),
                  "송장을 등록하고 출고 처리했습니다.",
                );
              }}
            >
              <label className="flex flex-col gap-1">
                <span className="field-label">택배사</span>
                <select id="carrierId" name="carrierId" className="input w-36" required defaultValue="">
                  <option value="" disabled>
                    선택
                  </option>
                  {carriers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1">
                <span className="field-label">송장번호</span>
                <input id="trackingNo" name="trackingNo" className="input w-48 font-mono" inputMode="numeric" required />
              </label>
              <button type="submit" className="btn-primary" disabled={busy}>
                송장 등록·출고
              </button>
            </form>
          )}
        </section>
      )}

      <section className="card overflow-x-auto">
        <table className="w-full min-w-[40rem] text-sm">
          <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
            <tr>
              <th className="px-3 py-2 font-medium">상품</th>
              <th className="px-3 py-2 font-medium">상태</th>
              <th className="px-3 py-2 text-right font-medium">수량</th>
              <th className="px-3 py-2 text-right font-medium">금액</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.id} className="border-b border-line last:border-0">
                <td className="px-3 py-2">
                  <div className="flex items-center gap-3">
                    <Thumb src={item.thumbnailUrl} className="size-11" />
                    <span className="flex flex-col">
                      <span>{item.productName}</span>
                      <span className="text-xs text-ink-faint">
                        {[gradeLabel(item.conditionGrade), item.optionText].filter(Boolean).join(" · ")}
                      </span>
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2">{ITEM_STATUS_LABEL[item.status] ?? item.status}</td>
                <td className="px-3 py-2 text-right tabular-nums">{item.qty}</td>
                <td className="px-3 py-2 text-right tabular-nums">{formatWon(item.lineTotal)}</td>
                <td className="px-3 py-2 text-right">
                  {(item.status === "PAID" || item.status === "PREPARING") && order.items.length > 1 && (
                    <button
                      type="button"
                      className="text-xs text-danger underline underline-offset-2 disabled:opacity-50"
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          await claimAdminApi.cancelItem(orderNo, item.id, "판매자 취소 (재고 부족 등)");
                          return orderAdminApi.detail(orderNo);
                        }, `'${item.productName}'을(를) 취소하고 부분 환불했습니다.`)
                      }
                    >
                      이 상품 취소
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <div className="grid gap-5 md:grid-cols-2">
        <section className="card flex flex-col gap-1.5 p-4 text-sm">
          <h2 className="mb-1 font-semibold">받는 분</h2>
          <p>
            {order.receiver.name} · {order.receiver.phone}
          </p>
          <p className="text-ink-soft">
            ({order.receiver.zipcode}) {order.receiver.address1} {order.receiver.address2}
          </p>
          {order.receiver.deliveryMemo && <p className="text-ink-faint">요청: {order.receiver.deliveryMemo}</p>}
          <p className="mt-2 text-[11px] text-ink-faint">개인정보 열람 기록이 남습니다.</p>
        </section>
        <section className="card flex flex-col gap-1.5 p-4 text-sm">
          <h2 className="mb-1 font-semibold">결제</h2>
          <div className="flex justify-between">
            <span className="text-ink-soft">상품 금액</span>
            <span className="tabular-nums">{formatWon(order.itemsTotal)}</span>
          </div>
          {order.discountTotal > 0 && (
            <div className="flex justify-between">
              <span className="text-ink-soft">수량 할인</span>
              <span className="tabular-nums">−{formatWon(order.discountTotal)}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-ink-soft">배송비</span>
            <span className="tabular-nums">{formatWon(order.shippingFee)}</span>
          </div>
          <div className="flex justify-between font-semibold">
            <span>결제 금액</span>
            <span className="tabular-nums">{formatWon(order.payAmount)}</span>
          </div>
          {order.payment ? (
            <p className="text-xs text-ink-faint">
              {order.payment.easyPayProvider ?? order.payment.method} · {order.payment.status}
              {order.payment.approvedAt && ` · ${formatDate(order.payment.approvedAt)}`}
            </p>
          ) : (
            order.payMethod === "BANK_TRANSFER" && <p className="text-xs text-ink-faint">무통장입금 · 입금 전</p>
          )}
          {order.refunds.map((r) => (
            <p key={r.id} className="flex justify-between text-xs">
              <span className="text-ink-soft">
                환불 {r.status === "REQUESTED" ? "(송금 대기)" : "(완료)"} · {r.reason}
              </span>
              <span className="tabular-nums text-danger">−{formatWon(r.amount)}</span>
            </p>
          ))}
        </section>
      </div>

      {order.shipments.length > 0 && (
        <section className="card flex flex-col gap-1 p-4 text-sm">
          <h2 className="mb-1 font-semibold">출고</h2>
          {order.shipments.map((s) => (
            <p key={s.id}>
              {s.carrierName} <span className="font-mono">{s.trackingNo}</span>
              {s.shippedAt && <span className="text-ink-faint"> · {formatDate(s.shippedAt)}</span>}
            </p>
          ))}
        </section>
      )}

      {order.cancellable && (
        <section className="flex flex-col gap-2">
          {cancelOpen ? (
            <form
              className="card flex flex-wrap items-end gap-2 border-danger/30 p-4"
              onSubmit={async (e) => {
                e.preventDefault();
                const reason = String(new FormData(e.currentTarget).get("reason") ?? "").trim() || "판매자 취소";
                const done = awaitingDeposit
                  ? "입금 전 주문을 취소했습니다."
                  : order.payMethod === "BANK_TRANSFER"
                    ? "주문을 취소했습니다. 환불 금액을 고객 계좌로 보내 주세요."
                    : "주문을 취소하고 전액 환불했습니다.";
                if (await run(() => orderAdminApi.cancel(orderNo, reason), done)) {
                  setCancelOpen(false);
                }
              }}
            >
              <label className="flex min-w-60 flex-1 flex-col gap-1">
                <span className="field-label">취소 사유 (고객에게 안내됨)</span>
                <input id="reason" name="reason" className="input" placeholder="예: 재고 부족으로 판매 불가" />
              </label>
              <button type="button" className="btn-outline" onClick={() => setCancelOpen(false)}>
                닫기
              </button>
              <button type="submit" className="btn bg-danger text-white" disabled={busy}>
                {awaitingDeposit ? "미입금 취소" : "취소·전액 환불"}
              </button>
            </form>
          ) : (
            <button type="button" className="btn-outline self-start text-danger" onClick={() => setCancelOpen(true)}>
              주문 취소
            </button>
          )}
        </section>
      )}
    </div>
  );
}
