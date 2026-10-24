"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ProductImage } from "@/components/ProductImage";
import { cartApi, ClientApiError, loginUrl, type CartView } from "@/lib/client-api";
import { formatWon } from "@/lib/format";

export default function CartPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    cartApi
      .view()
      .then(setCart)
      .catch((e) => {
        if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
        else setError("장바구니를 불러오지 못했습니다.");
      });
  }, [router]);

  async function run(action: () => Promise<CartView>) {
    try {
      setCart(await action());
      setError(null);
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "처리하지 못했습니다.");
    }
  }

  if (!cart) {
    return <p className="py-10 text-sm text-ink-faint">{error ?? "불러오는 중…"}</p>;
  }

  const buyable = cart.items.filter((i) => i.purchasable);
  const unavailable = cart.items.filter((i) => !i.purchasable);

  function checkout() {
    const items = buyable.map((i) => `${i.skuId}:${i.qty}`).join(",");
    router.push(`/checkout?items=${items}&from=cart`);
  }

  return (
    <div className="flex flex-col gap-6 py-6">
      <h1 className="text-xl font-bold">장바구니</h1>

      {cart.items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-20 text-center">
          <p className="font-medium">장바구니가 비어 있습니다.</p>
          <Link href="/" className="text-sm text-brand underline underline-offset-4">
            상품 보러 가기
          </Link>
        </div>
      ) : (
        <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {cart.items.map((item) => (
              <li key={item.skuId} className={`flex gap-3 py-4 ${item.purchasable ? "" : "opacity-60"}`}>
                <Link href={`/products/${item.productId}`} className="shrink-0">
                  <ProductImage src={item.thumbnailUrl} alt={item.productName} className="size-20 rounded-md" />
                </Link>
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <Link href={`/products/${item.productId}`} className="line-clamp-2 text-sm hover:underline">
                    {item.productName}
                  </Link>
                  <p className="text-xs text-ink-faint">
                    {item.grade.label}
                    {item.optionText && ` · ${item.optionText}`}
                  </p>
                  {!item.purchasable && (
                    <p className="text-xs font-medium text-sale">
                      {item.available <= 0 ? "품절" : `남은 수량 ${item.available}개 - 수량을 줄여 주세요`}
                    </p>
                  )}
                  <div className="mt-1 flex items-center justify-between gap-2">
                    <div className="flex items-center rounded border border-line">
                      <button
                        type="button"
                        aria-label="수량 줄이기"
                        className="size-8 disabled:opacity-30"
                        disabled={item.qty <= 1}
                        onClick={() => run(() => cartApi.changeQty(item.skuId, item.qty - 1))}
                      >
                        −
                      </button>
                      <span className="w-8 text-center text-sm tabular-nums">{item.qty}</span>
                      <button
                        type="button"
                        aria-label="수량 늘리기"
                        className="size-8 disabled:opacity-30"
                        disabled={item.qty >= item.available}
                        onClick={() => run(() => cartApi.changeQty(item.skuId, item.qty + 1))}
                      >
                        +
                      </button>
                    </div>
                    <span className="font-bold tabular-nums">{formatWon(item.lineTotal)}</span>
                  </div>
                </div>
                <button
                  type="button"
                  aria-label={`${item.productName} 삭제`}
                  className="self-start text-lg text-ink-faint hover:text-ink"
                  onClick={() => run(() => cartApi.remove([item.skuId]))}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>

          <aside className="flex h-fit flex-col gap-3 rounded-md bg-surface-muted p-5 lg:sticky lg:top-24">
            <dl className="flex flex-col gap-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-ink-soft">상품 금액</dt>
                <dd className="tabular-nums">{formatWon(cart.itemsTotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-ink-soft">배송비</dt>
                <dd className="tabular-nums">{cart.shippingFee === 0 ? "무료" : formatWon(cart.shippingFee)}</dd>
              </div>
              <div className="flex justify-between border-t border-line pt-3 text-base font-bold">
                <dt>결제 예정 금액</dt>
                <dd className="tabular-nums">{formatWon(cart.payAmount)}</dd>
              </div>
            </dl>
            {unavailable.length > 0 && (
              <p className="text-xs text-ink-soft">품절·수량 초과 상품 {unavailable.length}개는 주문에서 빠집니다.</p>
            )}
            {error && <p className="text-xs text-sale">{error}</p>}
            <button
              type="button"
              onClick={checkout}
              disabled={buyable.length === 0}
              className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-40"
            >
              {buyable.length}개 상품 주문하기
            </button>
          </aside>
        </div>
      )}
    </div>
  );
}
