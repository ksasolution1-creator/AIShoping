"use client";

import { loadTossPayments, type TossPaymentsWidgets } from "@tosspayments/tosspayments-sdk";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { AddressSearch } from "@/components/AddressSearch";
import { TextField } from "@/components/AuthCard";
import { ProductImage } from "@/components/ProductImage";
import {
  authApi,
  ClientApiError,
  loginUrl,
  orderApi,
  type CheckoutConfig,
  type Me,
  type OrderLine,
  type PayMethod,
  type Preview,
} from "@/lib/client-api";
import { COMPANY } from "@/lib/company";
import { formatWon } from "@/lib/format";

/** 주소의 items=12:1,15:2 또는 sku=12&qty=1 을 주문 줄로 바꾼다 */
function parseLines(params: URLSearchParams): OrderLine[] {
  const items = params.get("items");
  if (items) {
    return items
      .split(",")
      .map((pair) => pair.split(":").map(Number))
      .filter(([skuId, qty]) => skuId > 0 && qty > 0)
      .map(([skuId, qty]) => ({ skuId, qty }));
  }
  const skuId = Number(params.get("sku"));
  const qty = Number(params.get("qty") ?? 1);
  return skuId > 0 && qty > 0 ? [{ skuId, qty }] : [];
}

type FormErrors = Record<string, string>;

export function CheckoutForm() {
  const router = useRouter();
  const params = useSearchParams();
  const lines = useMemo(() => parseLines(new URLSearchParams(params.toString())), [params]);

  const [me, setMe] = useState<Me | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [config, setConfig] = useState<CheckoutConfig | null>(null);
  const [method, setMethod] = useState<PayMethod>("BANK_TRANSFER");
  const [agreed, setAgreed] = useState(false);
  const [widgetsReady, setWidgetsReady] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const widgetsRef = useRef<TossPaymentsWidgets | null>(null);
  const [zipcode, setZipcode] = useState("");
  const [address1, setAddress1] = useState("");

  const selectAddress = useCallback((zip: string, address: string) => {
    setZipcode(zip);
    setAddress1(address);
    // 상세 주소 입력으로 바로 이동
    requestAnimationFrame(() => document.getElementById("address2")?.focus());
  }, []);

  // 1) 로그인 확인 → 주문 미리보기(서버 금액) → 결제 수단 준비 (카드를 켠 경우에만 결제위젯)
  useEffect(() => {
    if (lines.length === 0) return;
    let cancelled = false;
    (async () => {
      try {
        const user = await authApi.me();
        if (!user) {
          router.replace(loginUrl());
          return;
        }
        const [config, priced] = await Promise.all([orderApi.config(), orderApi.preview(lines)]);
        if (cancelled) return;
        setMe(user);
        setPreview(priced);
        setConfig(config);
        setMethod(config.bankReady ? "BANK_TRANSFER" : "TOSS");
        if (!config.cardReady || !priced.allPurchasable) return;

        const toss = await loadTossPayments(config.clientKey);
        const widgets = toss.widgets({ customerKey: config.customerKey });
        await widgets.setAmount({ currency: "KRW", value: priced.payAmount });
        await Promise.all([
          widgets.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" }),
          widgets.renderAgreement({ selector: "#agreement", variantKey: "AGREEMENT" }),
        ]);
        if (cancelled) return;
        widgetsRef.current = widgets;
        setWidgetsReady(true);
      } catch (e) {
        if (!cancelled) setMessage(e instanceof ClientApiError ? e.message : "결제 화면을 불러오지 못했습니다.");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [lines, router]);

  // 2) 주문하기: 서버에 주문을 만들고(재고 예약·금액 확정)
  //    무통장입금은 입금 안내가 있는 주문 상세로, 카드는 결제창을 띄운다
  async function pay(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const widgets = widgetsRef.current;
    if (!preview || (method === "TOSS" && !widgets)) return;
    const form = new FormData(event.currentTarget);
    const value = (key: string) => String(form.get(key) ?? "").trim();

    setSubmitting(true);
    setMessage(null);
    setErrors({});
    let orderNo: string | null = null;
    try {
      const checkout = await orderApi.create({
        items: lines,
        receiverName: value("receiverName"),
        receiverPhone: value("receiverPhone"),
        zipcode: value("zipcode"),
        address1: value("address1"),
        address2: value("address2"),
        deliveryMemo: value("deliveryMemo"),
        payMethod: method,
        depositorName: method === "BANK_TRANSFER" ? value("depositorName") : undefined,
      });
      if (method === "BANK_TRANSFER" || !widgets) {
        router.replace(`/orders/${checkout.orderNo}?placed=1`);
        return;
      }
      orderNo = checkout.orderNo;
      if (checkout.amount !== preview.payAmount) {
        // 그사이 가격·배송비가 바뀐 경우 서버 금액으로 결제한다
        await widgets.setAmount({ currency: "KRW", value: checkout.amount });
      }
      await widgets.requestPayment({
        orderId: checkout.orderNo,
        orderName: checkout.orderName,
        successUrl: `${window.location.origin}/checkout/success`,
        failUrl: `${window.location.origin}/checkout/fail`,
        customerName: checkout.customerName,
        customerEmail: checkout.customerEmail ?? undefined,
        customerMobilePhone: value("receiverPhone").replace(/\D/g, ""),
      });
    } catch (e) {
      if (orderNo) {
        // 결제창을 닫았거나 실패: 예약해 둔 재고를 바로 푼다
        await orderApi.abandon(orderNo).catch(() => undefined);
      }
      if (e instanceof ClientApiError) {
        setErrors(e.fieldErrors);
        setMessage(e.message);
      } else {
        const code = (e as { code?: string })?.code;
        setMessage(code === "USER_CANCEL" ? "결제를 취소했습니다." : "결제를 진행하지 못했습니다. 다시 시도해 주세요.");
      }
      setSubmitting(false);
    }
  }

  if (lines.length === 0) {
    return (
      <div className="flex flex-col items-start gap-3 text-sm text-ink-faint">
        <p>주문할 상품이 없습니다.</p>
        <div className="flex gap-3">
          <Link href="/cart" className="text-brand underline underline-offset-4">
            장바구니 보기
          </Link>
          <Link href="/" className="text-brand underline underline-offset-4">
            상품 둘러보기
          </Link>
        </div>
      </div>
    );
  }
  if (!preview || !me) {
    return message ? <p className="text-sm text-sale">{message}</p> : <WhaleLoader label="주문서를 준비하는 중이에요" />;
  }

  return (
    <form onSubmit={pay} className="grid gap-8 lg:grid-cols-[1fr_22rem]" noValidate>
      <div className="flex flex-col gap-8">
        <section className="flex flex-col gap-3">
          <h2 className="border-b border-ink pb-2 font-bold">주문 상품 {preview.lines.length}개</h2>
          <ul className="flex flex-col divide-y divide-line">
            {preview.lines.map((line) => (
              <li key={line.skuId} className="flex gap-3 py-3">
                <ProductImage src={line.thumbnailUrl} alt={line.productName} className="size-16 rounded-md" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5 text-sm">
                  <span className="line-clamp-1">{line.productName}</span>
                  <span className="text-xs text-ink-faint">
                    {line.conditionGrade}
                    {line.optionText && ` · ${line.optionText}`} · {line.qty}개
                  </span>
                  {!line.purchasable && (
                    <span className="text-xs font-medium text-sale">
                      {line.available <= 0 ? "품절되었습니다" : `남은 수량 ${line.available}개`}
                    </span>
                  )}
                  {line.unitDiscount > 0 && (
                    <span className="text-xs text-brand">수량 할인 개당 −{formatWon(line.unitDiscount)}</span>
                  )}
                </div>
                <span className="text-sm font-bold tabular-nums">{formatWon(line.lineTotal)}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="border-b border-ink pb-2 font-bold">배송지</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField id="receiverName" label="받는 분" defaultValue={me.name} autoComplete="name" error={errors.receiverName} />
            <TextField
              id="receiverPhone"
              label="연락처"
              type="tel"
              inputMode="tel"
              placeholder="010-1234-5678"
              autoComplete="tel"
              error={errors.receiverPhone}
            />
            <div className="flex items-end gap-2 sm:col-span-2">
              <div className="w-36">
                <TextField
                  id="zipcode"
                  label="우편번호"
                  inputMode="numeric"
                  maxLength={5}
                  placeholder="5자리"
                  autoComplete="postal-code"
                  value={zipcode}
                  onChange={(e) => setZipcode(e.target.value)}
                  error={errors.zipcode}
                />
              </div>
              <AddressSearch onSelect={selectAddress} />
            </div>
            <div className="sm:col-span-2">
              <TextField
                id="address1"
                label="주소"
                autoComplete="address-line1"
                value={address1}
                onChange={(e) => setAddress1(e.target.value)}
                error={errors.address1}
              />
            </div>
            <div className="sm:col-span-2">
              <TextField id="address2" label="상세 주소" autoComplete="address-line2" error={errors.address2} />
            </div>
            <div className="sm:col-span-2">
              <TextField id="deliveryMemo" label="배송 요청사항 (선택)" placeholder="예: 문 앞에 놓아주세요" />
            </div>
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="border-b border-ink pb-2 font-bold">결제 수단</h2>
          {config && !config.bankReady && !config.cardReady ? (
            <p className="rounded-md bg-surface-muted p-4 text-sm text-ink-soft">결제 준비가 아직 끝나지 않았습니다.</p>
          ) : !preview.allPurchasable ? (
            <div className="rounded-md bg-sale/10 p-4 text-sm text-sale">
              {preview.notices.length > 0 ? (
                <ul className="flex flex-col gap-1">
                  {preview.notices.map((n) => (
                    <li key={n}>{n}</li>
                  ))}
                </ul>
              ) : (
                <p>구매할 수 없는 상품이 있습니다.</p>
              )}
              <p className="mt-1">장바구니에서 수량을 조정해 주세요.</p>
            </div>
          ) : (
            <>
              {config?.bankReady && config.cardReady && (
                <div role="radiogroup" aria-label="결제 수단" className="grid grid-cols-2 gap-2">
                  {(
                    [
                      ["BANK_TRANSFER", "무통장입금"],
                      ["TOSS", "카드·간편결제"],
                    ] as const
                  ).map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      role="radio"
                      aria-checked={method === value}
                      onClick={() => setMethod(value)}
                      className={`h-11 rounded-md border text-sm font-medium ${
                        method === value ? "border-brand bg-brand/5 text-brand" : "border-line text-ink-soft"
                      }`}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              )}
              {method === "BANK_TRANSFER" && config?.bankReady && (
                <div className="flex flex-col gap-4 rounded-md border border-line p-4 text-sm">
                  <dl className="grid grid-cols-[5rem_1fr] gap-y-1">
                    <dt className="text-ink-soft">입금 계좌</dt>
                    <dd className="font-semibold">
                      {config.bank.bankName} <span className="font-mono tabular-nums">{config.bank.accountNo}</span>
                    </dd>
                    <dt className="text-ink-soft">예금주</dt>
                    <dd>{config.bank.accountHolder}</dd>
                  </dl>
                  <TextField
                    id="depositorName"
                    label="입금자명"
                    defaultValue={me.name}
                    maxLength={20}
                    error={errors.depositorName}
                  />
                  <p className="text-xs leading-relaxed text-ink-faint">
                    주문 후 {config.bank.depositHours}시간 안에 주문 금액을 위 계좌로 보내 주세요. 입금이 확인되면 상품
                    준비를 시작합니다. 기한 안에 입금하지 않으면 주문이 자동으로 취소됩니다.
                  </p>
                  <label className="flex items-start gap-2">
                    <input
                      type="checkbox"
                      checked={agreed}
                      onChange={(e) => setAgreed(e.target.checked)}
                      className="mt-0.5 size-4 accent-brand"
                    />
                    <span>
                      주문 상품, 금액, 배송 정보와{" "}
                      <a href="/policy" target="_blank" rel="noreferrer" className="underline underline-offset-2">
                        배송·교환·환불 안내
                      </a>
                      를 확인했으며 구매에 동의합니다.
                    </span>
                  </label>
                  {COMPANY.escrow && (
                    <p className="text-xs text-ink-faint">
                      고객님의 안전거래를 위해 현금 결제 시 {COMPANY.escrow} 구매안전서비스를 이용하실 수 있습니다.
                    </p>
                  )}
                </div>
              )}
              <div hidden={method !== "TOSS"}>
                <div id="payment-method" />
                <div id="agreement" />
              </div>
            </>
          )}
        </section>
      </div>

      <aside className="flex h-fit flex-col gap-3 rounded-md bg-surface-muted p-5 lg:sticky lg:top-24">
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-ink-soft">상품 금액</dt>
            <dd className="tabular-nums">{formatWon(preview.itemsTotal)}</dd>
          </div>
          {preview.discountTotal > 0 && (
            <div className="flex justify-between text-brand">
              <dt>수량 할인</dt>
              <dd className="tabular-nums">−{formatWon(preview.discountTotal)}</dd>
            </div>
          )}
          <div className="flex justify-between">
            <dt className="text-ink-soft">배송비</dt>
            <dd className="tabular-nums">{preview.shippingFee === 0 ? "무료" : formatWon(preview.shippingFee)}</dd>
          </div>
          <div className="flex justify-between border-t border-line pt-3 text-base font-bold">
            <dt>최종 결제 금액</dt>
            <dd className="tabular-nums">{formatWon(preview.payAmount)}</dd>
          </div>
        </dl>
        {message && (
          <p role="alert" className="rounded-md bg-sale/10 p-3 text-sm text-sale">
            {message}
          </p>
        )}
        {method === "BANK_TRANSFER" ? (
          <button
            type="submit"
            disabled={!config?.bankReady || !preview.allPurchasable || !agreed || submitting}
            className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-40"
          >
            {submitting ? "주문하는 중…" : `${formatWon(preview.payAmount)} 주문하기`}
          </button>
        ) : (
          <>
            <button
              type="submit"
              disabled={!widgetsReady || submitting}
              className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-40"
            >
              {submitting ? "결제 진행 중…" : `${formatWon(preview.payAmount)} 결제하기`}
            </button>
            <p className="text-xs leading-relaxed text-ink-faint">
              결제창을 띄운 뒤 30분 안에 결제하지 않으면 주문이 자동으로 취소됩니다.
            </p>
          </>
        )}
      </aside>
    </form>
  );
}
