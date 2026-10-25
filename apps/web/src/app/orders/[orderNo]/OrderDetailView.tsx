"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ProductImage } from "@/components/ProductImage";
import {
  claimApi,
  CLAIM_STATUS_LABEL,
  communityApi,
  gradeLabel,
  ClientApiError,
  BANKS,
  ITEM_STATUS_LABEL,
  loginUrl,
  orderApi,
  RETURN_REASONS,
  type Claim,
  type OrderDetail,
} from "@/lib/client-api";
import { formatWon } from "@/lib/format";

type Item = OrderDetail["items"][number];

// 구매확정 전까지만 온라인 반품 신청을 받는다
const RETURNABLE = new Set(["SHIPPED", "DELIVERED"]);
const CONFIRMABLE = new Set(["SHIPPED", "DELIVERED"]);
const OPEN_CLAIM = new Set(["REQUESTED", "APPROVED", "COLLECTING", "INSPECTING"]);

export function OrderDetailView({ orderNo }: { orderNo: string }) {
  const router = useRouter();
  const justPlaced = useSearchParams().get("placed") === "1";
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(
    () =>
      orderApi
        .detail(orderNo)
        .then(setOrder)
        .catch((e) => {
          if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
          else setError(e instanceof ClientApiError ? e.message : "주문을 불러오지 못했습니다.");
        }),
    [orderNo, router],
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
      return true;
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "처리하지 못했습니다.");
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (!order) return error ? <p className="py-10 text-sm text-sale">{error}</p> : <WhaleLoader label="주문을 불러오는 중이에요" />;

  const claimsByItem = new Map<number, Claim[]>();
  order.claims.forEach((c) => claimsByItem.set(c.orderItemId, [...(claimsByItem.get(c.orderItemId) ?? []), c]));
  const refundedTotal = order.claims.reduce((sum, c) => sum + (c.refundAmount ?? 0), 0);
  const awaitingDeposit = order.status === "PENDING_PAYMENT" && order.bankDeposit !== null;
  const pendingRefund = order.refunds.filter((r) => r.status === "REQUESTED").reduce((sum, r) => sum + r.amount, 0);
  // 입금이 끝난 무통장입금 주문은 취소·반품 전에 환불 계좌가 있어야 한다
  const paidByBank = order.payMethod === "BANK_TRANSFER" && order.payment !== null;
  const needsRefundAccount = paidByBank && order.refundAccount === null;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 py-6">
      <div className="flex flex-col gap-1">
        <Link href="/orders" className="text-sm text-ink-faint hover:text-ink-soft">
          ← 주문 내역
        </Link>
        <h1 className="text-2xl font-bold text-navy">주문 상세</h1>
        <p className="text-sm text-ink-faint">
          {new Date(order.orderedAt).toLocaleString("ko-KR")} · <span className="font-mono">{order.orderNo}</span>
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-xl bg-sale/10 p-3 text-sm text-sale">
          {error}
        </p>
      )}

      {awaitingDeposit && order.bankDeposit && (
        <section className="flex flex-col gap-3 rounded-2xl border-2 border-brand/40 bg-brand/5 p-5 text-sm">
          <h2 className="text-base font-bold text-navy">
            {justPlaced ? "주문이 접수되었습니다. 아래 계좌로 입금해 주세요." : "입금을 기다리고 있어요"}
          </h2>
          <dl className="grid grid-cols-[5.5rem_1fr] gap-y-1.5">
            <dt className="text-ink-soft">입금 계좌</dt>
            <dd className="font-semibold">
              {order.bankDeposit.bankName}{" "}
              <span className="font-mono tabular-nums">{order.bankDeposit.accountNo}</span>
              <button
                type="button"
                onClick={() => navigator.clipboard?.writeText(order.bankDeposit!.accountNo.replace(/-/g, ""))}
                className="ml-2 text-xs font-normal text-brand underline underline-offset-2"
              >
                복사
              </button>
            </dd>
            <dt className="text-ink-soft">예금주</dt>
            <dd>{order.bankDeposit.accountHolder}</dd>
            <dt className="text-ink-soft">입금 금액</dt>
            <dd className="font-bold text-sale tabular-nums">{formatWon(order.payAmount)}</dd>
            <dt className="text-ink-soft">입금자명</dt>
            <dd>{order.bankDeposit.depositorName}</dd>
            {order.bankDeposit.dueAt && (
              <>
                <dt className="text-ink-soft">입금 기한</dt>
                <dd>{new Date(order.bankDeposit.dueAt).toLocaleString("ko-KR")}까지</dd>
              </>
            )}
          </dl>
          <p className="text-xs leading-relaxed text-ink-faint">
            입금자명과 금액이 주문과 같아야 확인이 빠릅니다. 입금이 확인되면 상품 준비를 시작하며, 기한이 지나면 주문이
            자동으로 취소됩니다.
          </p>
        </section>
      )}

      {pendingRefund > 0 && (
        <p className="rounded-xl bg-surface-muted p-4 text-sm text-ink-soft">
          환불 금액 {formatWon(pendingRefund)}을{" "}
          {order.refundAccount
            ? `입력하신 계좌(${order.refundAccount.bankName} ${order.refundAccount.accountNo})로 3영업일 안에 보내 드립니다.`
            : "아래 환불 계좌로 보내 드립니다. 계좌를 입력해 주세요."}
        </p>
      )}

      {paidByBank && (
        <RefundAccountSection
          account={order.refundAccount}
          busy={busy}
          onSave={(account) => run(() => orderApi.saveRefundAccount(order.orderNo, account))}
        />
      )}

      <section className="flex flex-col divide-y divide-line rounded-2xl border border-line">
        {order.items.map((item) => (
          <ItemRow
            key={item.id}
            item={item}
            claims={claimsByItem.get(item.id) ?? []}
            busy={busy}
            onCancel={() => run(() => claimApi.cancelItem(order.orderNo, item.id))}
            onReturn={(reason, detail) => run(() => claimApi.requestReturn(order.orderNo, item.id, reason, detail))}
            onWithdraw={(claimId) => run(() => claimApi.withdraw(claimId))}
            onConfirm={() => run(() => orderApi.confirmPurchase(order.orderNo, item.id))}
            onReview={(rating, content) => run(() => communityApi.writeReview(order.orderNo, item.id, rating, content))}
            singleItem={order.items.length === 1}
            needsRefundAccount={needsRefundAccount}
          />
        ))}
      </section>

      {order.shipments.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="font-bold">배송 정보</h2>
          {order.shipments.map((s) => (
            <p key={s.id} className="flex items-center justify-between gap-3 rounded-xl bg-surface-muted p-3 text-sm">
              <span>
                {s.carrierName} <span className="font-mono">{s.trackingNo}</span>
              </span>
              {s.trackingUrl && (
                <a href={s.trackingUrl} target="_blank" rel="noreferrer" className="shrink-0 font-semibold text-brand underline">
                  배송 조회
                </a>
              )}
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

      <section className="flex flex-col gap-2 rounded-2xl bg-surface-muted p-4 text-sm">
        <div className="flex justify-between">
          <span className="text-ink-soft">상품 금액</span>
          <span className="tabular-nums">{formatWon(order.itemsTotal)}</span>
        </div>
        {order.discountTotal > 0 && (
          <div className="flex justify-between text-brand">
            <span>수량 할인</span>
            <span className="tabular-nums">−{formatWon(order.discountTotal)}</span>
          </div>
        )}
        <div className="flex justify-between">
          <span className="text-ink-soft">배송비</span>
          <span className="tabular-nums">{order.shippingFee === 0 ? "무료" : formatWon(order.shippingFee)}</span>
        </div>
        <div className="flex justify-between border-t border-line pt-2 font-bold">
          <span>결제 금액</span>
          <span className="tabular-nums">{formatWon(order.payAmount)}</span>
        </div>
        {order.payMethod === "BANK_TRANSFER" && !order.payment && (
          <p className="text-xs text-ink-faint">무통장입금 · 입금 확인 전</p>
        )}
        {refundedTotal > 0 && (
          <div className="flex justify-between font-bold text-sale">
            <span>환불 금액</span>
            <span className="tabular-nums">−{formatWon(refundedTotal)}</span>
          </div>
        )}
        {order.payment && (
          <p className="text-xs text-ink-faint">{order.payment.easyPayProvider ?? order.payment.method}</p>
        )}
      </section>

      {order.cancellable &&
        (order.items.length > 1 || awaitingDeposit) &&
        (confirmingCancel ? (
          <div className="flex flex-col gap-3 rounded-2xl border border-sale/30 p-4">
            <p className="text-sm">
              {awaitingDeposit
                ? "아직 입금 전이라 주문만 취소됩니다. 취소할까요?"
                : `주문 전체를 취소하면 결제 금액 ${formatWon(order.payAmount)}이 환불됩니다. 취소할까요?`}
            </p>
            {needsRefundAccount && <RefundAccountNotice />}
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setConfirmingCancel(false)} className="h-11 rounded-xl border border-line text-sm">
                그대로 두기
              </button>
              <button
                type="button"
                disabled={busy || needsRefundAccount}
                onClick={async () => {
                  if (await run(() => orderApi.cancel(orderNo, "고객 요청 취소"))) setConfirmingCancel(false);
                }}
                className="h-11 rounded-xl bg-sale text-sm font-semibold text-white disabled:opacity-50"
              >
                {busy ? "취소 중…" : awaitingDeposit ? "주문 취소" : "전체 취소"}
              </button>
            </div>
          </div>
        ) : (
          <button type="button" onClick={() => setConfirmingCancel(true)} className="h-11 rounded-xl border border-line text-sm">
            {awaitingDeposit ? "주문 취소" : "주문 전체 취소"}
          </button>
        ))}
    </div>
  );
}

function ItemRow({
  item,
  claims,
  busy,
  singleItem,
  needsRefundAccount,
  onCancel,
  onReturn,
  onWithdraw,
  onConfirm,
  onReview,
}: {
  item: Item;
  claims: Claim[];
  busy: boolean;
  singleItem: boolean;
  needsRefundAccount: boolean;
  onCancel: () => Promise<boolean>;
  onReturn: (reason: string, detail: string) => Promise<boolean>;
  onWithdraw: (claimId: number) => Promise<boolean>;
  onConfirm: () => Promise<boolean>;
  onReview: (rating: number, content: string) => Promise<boolean>;
}) {
  const [mode, setMode] = useState<"none" | "cancel" | "return" | "confirm" | "review">("none");
  const [reason, setReason] = useState<string>("");
  const hasOpenClaim = claims.some((c) => OPEN_CLAIM.has(c.status));
  const canCancel = item.status === "PAID" && !hasOpenClaim;
  const canReturn = RETURNABLE.has(item.status) && !hasOpenClaim;
  const canConfirm = CONFIRMABLE.has(item.status) && !hasOpenClaim;
  const canReview = item.status === "CONFIRMED" && !item.reviewed;
  const reasonInfo = RETURN_REASONS.find((r) => r.code === reason);

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="flex gap-3">
        <ProductImage src={item.thumbnailUrl} alt={item.productName} className="size-16 rounded-xl" />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5 text-sm">
          <span className="font-semibold text-brand">{ITEM_STATUS_LABEL[item.status] ?? item.status}</span>
          <span className="line-clamp-1">{item.productName}</span>
          <span className="text-xs text-ink-faint">
            {[gradeLabel(item.conditionGrade), item.optionText].filter(Boolean).join(" · ")} · {item.qty}개
          </span>
        </div>
        <span className="text-sm font-bold tabular-nums">{formatWon(item.lineTotal)}</span>
      </div>

      {claims.map((claim) => (
        <div key={claim.id} className="flex flex-col gap-1 rounded-xl bg-surface-muted p-3 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-semibold">
              {claim.type === "CANCEL" ? "취소" : "반품"} · {CLAIM_STATUS_LABEL[claim.status]}
            </span>
            {claim.status === "REQUESTED" && claim.type === "RETURN" && (
              <button
                type="button"
                disabled={busy}
                onClick={() => onWithdraw(claim.id)}
                className="text-ink-soft underline underline-offset-2"
              >
                신청 철회
              </button>
            )}
          </div>
          {claim.type === "RETURN" && <span className="text-ink-soft">사유: {claim.reasonLabel}</span>}
          {claim.returnShippingFee > 0 && (
            <span className="text-ink-soft">반품 배송비 {formatWon(claim.returnShippingFee)} (환불 금액에서 차감)</span>
          )}
          {claim.refundAmount !== null && (
            <span className="font-semibold text-sale">환불 {formatWon(claim.refundAmount)}</span>
          )}
          {claim.rejectReason && <span className="text-sale">거절 사유: {claim.rejectReason}</span>}
        </div>
      ))}

      {mode === "none" && (canCancel || canReturn || canConfirm || canReview) && (
        <div className="flex flex-wrap gap-2">
          {canConfirm && (
            <button
              type="button"
              onClick={() => setMode("confirm")}
              className="h-9 rounded-lg bg-brand px-3 text-sm font-semibold text-white"
            >
              구매확정
            </button>
          )}
          {canReview && (
            <button
              type="button"
              onClick={() => setMode("review")}
              className="h-9 rounded-lg bg-brand px-3 text-sm font-semibold text-white"
            >
              후기 쓰기
            </button>
          )}
          {canCancel && !singleItem && (
            <button type="button" onClick={() => setMode("cancel")} className="h-9 rounded-lg border border-line px-3 text-sm">
              이 상품 취소
            </button>
          )}
          {canCancel && singleItem && (
            <button type="button" onClick={() => setMode("cancel")} className="h-9 rounded-lg border border-line px-3 text-sm">
              주문 취소
            </button>
          )}
          {canReturn && (
            <button type="button" onClick={() => setMode("return")} className="h-9 rounded-lg border border-line px-3 text-sm">
              반품 신청
            </button>
          )}
        </div>
      )}

      {mode === "confirm" && (
        <div className="flex flex-col gap-2 rounded-xl border border-brand/30 p-3 text-sm">
          <p>상품을 잘 받으셨나요? 구매확정하면 온라인 반품 신청은 할 수 없고, 후기를 쓸 수 있습니다.</p>
          <div className="flex gap-2">
            <button type="button" onClick={() => setMode("none")} className="h-9 flex-1 rounded-lg border border-line">
              나중에
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={async () => {
                if (await onConfirm()) setMode("none");
              }}
              className="h-9 flex-1 rounded-lg bg-brand font-semibold text-white disabled:opacity-50"
            >
              구매확정
            </button>
          </div>
        </div>
      )}

      {mode === "review" && (
        <ReviewForm
          busy={busy}
          onCancel={() => setMode("none")}
          onSubmit={async (rating, content) => {
            if (await onReview(rating, content)) setMode("none");
          }}
        />
      )}

      {mode === "cancel" && (
        <div className="flex flex-col gap-2 rounded-xl border border-sale/30 p-3 text-sm">
          <p>{singleItem ? "주문을 취소하면 결제 금액이 모두 환불됩니다." : `이 상품 ${formatWon(item.lineTotal)}을 취소하고 환불합니다.`}</p>
          {needsRefundAccount && <RefundAccountNotice />}
          <div className="flex gap-2">
            <button type="button" onClick={() => setMode("none")} className="h-9 flex-1 rounded-lg border border-line">
              그대로 두기
            </button>
            <button
              type="button"
              disabled={busy || needsRefundAccount}
              onClick={async () => {
                if (await onCancel()) setMode("none");
              }}
              className="h-9 flex-1 rounded-lg bg-sale font-semibold text-white disabled:opacity-50"
            >
              취소하기
            </button>
          </div>
        </div>
      )}

      {mode === "return" && (
        <form
          className="flex flex-col gap-2 rounded-xl border border-line p-3 text-sm"
          onSubmit={async (e) => {
            e.preventDefault();
            const detail = String(new FormData(e.currentTarget).get("detail") ?? "");
            if (await onReturn(reason, detail)) setMode("none");
          }}
        >
          <label className="flex flex-col gap-1">
            <span className="font-medium">반품 사유</span>
            <select
              id={`reason-${item.id}`}
              required
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="h-10 rounded-lg border border-line px-2"
            >
              <option value="" disabled>
                선택해 주세요
              </option>
              {RETURN_REASONS.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.label}
                </option>
              ))}
            </select>
          </label>
          {reasonInfo && (
            <p className={`text-xs ${reasonInfo.customerPays ? "text-sale" : "text-brand"}`}>
              {reasonInfo.customerPays
                ? "단순 변심은 반품 배송비(무료배송 주문이면 왕복)가 환불 금액에서 빠집니다."
                : "판매자 사유라 반품 배송비가 없습니다. 검수 후 전액 환불됩니다."}
            </p>
          )}
          <textarea
            id={`detail-${item.id}`}
            name="detail"
            rows={2}
            placeholder="상세 내용 (선택)"
            className="rounded-lg border border-line p-2"
          />
          {needsRefundAccount && <RefundAccountNotice />}
          <div className="flex gap-2">
            <button type="button" onClick={() => setMode("none")} className="h-9 flex-1 rounded-lg border border-line">
              닫기
            </button>
            <button type="submit" disabled={busy || !reason || needsRefundAccount} className="h-9 flex-1 rounded-lg bg-brand font-semibold text-white disabled:opacity-50">
              반품 신청
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function ReviewForm({
  busy,
  onCancel,
  onSubmit,
}: {
  busy: boolean;
  onCancel: () => void;
  onSubmit: (rating: number, content: string) => Promise<void>;
}) {
  const [rating, setRating] = useState(0);
  const [content, setContent] = useState("");

  return (
    <form
      className="flex flex-col gap-2 rounded-xl border border-line p-3 text-sm"
      onSubmit={(e) => {
        e.preventDefault();
        void onSubmit(rating, content.trim());
      }}
    >
      <fieldset className="flex items-center gap-1">
        <legend className="sr-only">별점</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="cursor-pointer text-2xl leading-none" title={`${n}점`}>
            <input type="radio" name="rating" value={n} className="sr-only" onChange={() => setRating(n)} />
            <span aria-hidden className={n <= rating ? "text-tag" : "text-line"}>
              ★
            </span>
            <span className="sr-only">{n}점</span>
          </label>
        ))}
        <span className="ml-2 text-xs text-ink-faint">{rating > 0 ? `${rating}점` : "별점을 골라 주세요"}</span>
      </fieldset>
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        rows={3}
        minLength={10}
        maxLength={1000}
        required
        placeholder="상품 상태, 포장, 배송은 어땠나요? (10자 이상)"
        className="rounded-lg border border-line p-2"
      />
      <div className="flex gap-2">
        <button type="button" onClick={onCancel} className="h-9 flex-1 rounded-lg border border-line">
          닫기
        </button>
        <button
          type="submit"
          disabled={busy || rating === 0 || content.trim().length < 10}
          className="h-9 flex-1 rounded-lg bg-brand font-semibold text-white disabled:opacity-50"
        >
          후기 올리기
        </button>
      </div>
    </form>
  );
}

function RefundAccountNotice() {
  return <p className="text-xs font-medium text-sale">환불받을 계좌를 위 &lsquo;환불 계좌&rsquo;에 먼저 입력해 주세요.</p>;
}

/** 무통장입금 환불 계좌: 입금 후 취소·반품하려면 필요하다 */
function RefundAccountSection({
  account,
  busy,
  onSave,
}: {
  account: OrderDetail["refundAccount"];
  busy: boolean;
  onSave: (account: { bankName: string; accountNo: string; holder: string }) => Promise<boolean>;
}) {
  const [editing, setEditing] = useState(account === null);

  if (account && !editing) {
    return (
      <section className="flex items-center justify-between gap-3 rounded-2xl border border-line p-4 text-sm">
        <div className="flex flex-col gap-0.5">
          <span className="font-bold">환불 계좌</span>
          <span className="text-ink-soft">
            {account.bankName} <span className="font-mono tabular-nums">{account.accountNo}</span> · {account.holder}
          </span>
        </div>
        <button type="button" onClick={() => setEditing(true)} className="shrink-0 text-xs text-ink-soft underline">
          변경
        </button>
      </section>
    );
  }

  return (
    <form
      className="flex flex-col gap-3 rounded-2xl border border-line p-4 text-sm"
      onSubmit={async (e) => {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const value = (key: string) => String(data.get(key) ?? "").trim();
        if (await onSave({ bankName: value("bankName"), accountNo: value("accountNo"), holder: value("holder") })) {
          setEditing(false);
        }
      }}
    >
      <div className="flex flex-col gap-0.5">
        <span className="font-bold">환불 계좌</span>
        <span className="text-xs text-ink-faint">
          무통장입금 주문을 취소·반품하면 이 계좌로 환불해 드립니다. 계좌번호는 암호화해서 보관합니다.
        </span>
      </div>
      <div className="grid gap-2 sm:grid-cols-[9rem_1fr_7rem]">
        <select
          name="bankName"
          required
          defaultValue={account?.bankName ?? ""}
          aria-label="은행"
          className="h-10 rounded-lg border border-line px-2"
        >
          <option value="" disabled>
            은행 선택
          </option>
          {BANKS.map((b) => (
            <option key={b} value={b}>
              {b}
            </option>
          ))}
        </select>
        <input
          name="accountNo"
          required
          inputMode="numeric"
          pattern="[0-9\-]{8,20}"
          placeholder="계좌번호 (숫자만)"
          defaultValue={account?.accountNo ?? ""}
          aria-label="계좌번호"
          className="h-10 rounded-lg border border-line px-3 font-mono"
        />
        <input
          name="holder"
          required
          maxLength={30}
          placeholder="예금주"
          defaultValue={account?.holder ?? ""}
          aria-label="예금주"
          className="h-10 rounded-lg border border-line px-3"
        />
      </div>
      <div className="flex gap-2">
        {account && (
          <button type="button" onClick={() => setEditing(false)} className="h-9 flex-1 rounded-lg border border-line">
            취소
          </button>
        )}
        <button type="submit" disabled={busy} className="h-9 flex-1 rounded-lg bg-brand font-semibold text-white disabled:opacity-50">
          저장
        </button>
      </div>
    </form>
  );
}
