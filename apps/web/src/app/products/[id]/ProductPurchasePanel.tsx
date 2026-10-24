"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import { GradeBadge } from "@/components/GradeBadge";
import { PriceTag } from "@/components/PriceTag";
import { ProductImage } from "@/components/ProductImage";
import type { ProductDetail } from "@/lib/api";
import { cartApi, ClientApiError, communityApi, loginUrl } from "@/lib/client-api";
import { formatRate, formatWon, optionText, shipWithinText, tierBpFor, tierUnitDiscount } from "@/lib/format";

export function ProductPurchasePanel({ product }: { product: ProductDetail }) {
  const router = useRouter();
  const firstAvailable = product.skus.find((s) => s.availableQty > 0) ?? product.skus[0];
  const [skuId, setSkuId] = useState(firstAvailable?.id);
  const { minOrderQty, shipWithinDays, tiers } = product.wholesale;
  const [qty, setQty] = useState(minOrderQty);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const sku = product.skus.find((s) => s.id === skuId);

  async function addToCart() {
    if (!sku) return;
    setBusy(true);
    setNotice(null);
    try {
      await cartApi.add(sku.id, qty);
      setNotice({ kind: "ok", text: "장바구니에 담았습니다." });
    } catch (e) {
      if (e instanceof ClientApiError && e.status === 401) {
        router.push(loginUrl());
        return;
      }
      setNotice({ kind: "error", text: e instanceof ClientApiError ? e.message : "담지 못했습니다." });
    } finally {
      setBusy(false);
    }
  }

  function buyNow() {
    if (!sku) return;
    router.push(`/checkout?sku=${sku.id}&qty=${qty}`);
  }

  // 선택한 상태등급의 실물 사진을 먼저, 그다음 상품 공통 사진
  const images = useMemo(() => {
    const own = product.images.filter((i) => i.skuId === skuId);
    const common = product.images.filter((i) => i.skuId === null);
    return [...own, ...common];
  }, [product.images, skuId]);
  const [imageIndex, setImageIndex] = useState(0);
  const current = images[Math.min(imageIndex, images.length - 1)];

  const maxQty = sku ? Math.max(sku.availableQty, 0) : 0;
  const totalStock = product.skus.reduce((sum, s) => sum + Math.max(s.availableQty, 0), 0);
  // 최소구매수량보다 재고가 적으면 살 수 없다
  const canBuy = maxQty > 0 && maxQty >= minOrderQty;
  // 이 화면에서는 고른 상태 하나의 수량으로 계산한다 (장바구니에서는 같은 상품의 다른 상태와 합산)
  const bp = tierBpFor(tiers, qty);
  const unitPrice = sku ? sku.price - tierUnitDiscount(sku.price, bp) : 0;

  function changeQty(next: number) {
    if (!Number.isFinite(next)) return;
    setQty(Math.min(maxQty, Math.max(minOrderQty, Math.floor(next))));
  }

  return (
    <div className="grid gap-8 md:grid-cols-2 md:gap-10">
      <div className="flex flex-col gap-3">
        <ProductImage
          src={current?.url ?? null}
          alt={product.name}
          className="aspect-square w-full rounded-md"
        />
        {images.length > 1 && (
          <ul className="flex gap-2 overflow-x-auto">
            {images.map((image, index) => (
              <li key={image.id}>
                <button
                  type="button"
                  onClick={() => setImageIndex(index)}
                  aria-label={`사진 ${index + 1}`}
                  className={`block overflow-hidden rounded ring-2 ${
                    index === imageIndex ? "ring-ink" : "ring-transparent"
                  }`}
                >
                  <ProductImage src={image.url} alt="" className="size-16" />
                </button>
              </li>
            ))}
          </ul>
        )}
        {current?.skuId !== null && current && (
          <p className="text-xs text-ink-faint">선택한 상태등급 상품의 실물 사진입니다.</p>
        )}
      </div>

      <div className="flex flex-col gap-5">
        <div className="flex items-start gap-3">
          <div className="flex flex-1 flex-col gap-1.5">
            {product.brand && <span className="text-sm text-ink-faint">{product.brand}</span>}
            <h1 className="text-xl leading-snug font-bold sm:text-2xl">{product.name}</h1>
            {(product.businessOnly || product.imageReuseAllowed) && (
              <div className="flex flex-wrap gap-1.5 text-xs">
                {product.businessOnly && (
                  <span className="rounded bg-navy px-2 py-0.5 font-bold text-white">사업자 회원 전용</span>
                )}
                {product.imageReuseAllowed && (
                  <span className="rounded border border-brand px-2 py-0.5 text-brand">상세이미지 사용 허용</span>
                )}
              </div>
            )}
          </div>
          <WishButton productId={product.id} />
        </div>

        {sku && <PriceTag price={sku.price} listPrice={sku.listPrice} size="lg" />}

        <dl className="grid grid-cols-[6.5rem_1fr] gap-y-1.5 border-y border-line py-3 text-sm">
          <dt className="text-ink-faint">구매수량</dt>
          <dd>{minOrderQty > 1 ? <strong>최소 {minOrderQty.toLocaleString("ko-KR")}개부터</strong> : "1개부터"}</dd>
          <dt className="text-ink-faint">재고수량</dt>
          <dd className="tabular-nums">{totalStock.toLocaleString("ko-KR")}개</dd>
          <dt className="text-ink-faint">출고</dt>
          <dd>{shipWithinText(shipWithinDays)}</dd>
          <dt className="text-ink-faint">배송</dt>
          <dd>
            {product.shippingFee === 0 ? "무료배송" : `택배 ${formatWon(product.shippingFee)}`}
            <span className="text-ink-faint"> · 같은 판매자 상품은 묶음배송(배송비 한 번)</span>
          </dd>
        </dl>

        {tiers.length > 0 && (
          <div className="rounded-md border border-tag bg-tag/10 p-3">
            <p className="mb-2 text-sm font-semibold">많이 살수록 싸게 · 수량별 할인</p>
            <ul className="grid grid-cols-2 gap-1.5 text-sm sm:grid-cols-3">
              {tiers.map((t) => (
                <li
                  key={t.minQty}
                  className={`rounded px-2 py-1.5 text-center ${
                    t.discountBp === bp ? "bg-brand font-semibold text-white" : "bg-surface"
                  }`}
                >
                  {t.minQty.toLocaleString("ko-KR")}개 이상 <strong>{formatRate(t.discountBp)}</strong>
                  {sku && (
                    <span className="block text-xs opacity-80">
                      개당 {formatWon(sku.price - tierUnitDiscount(sku.price, t.discountBp))}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            <p className="mt-2 text-xs text-ink-soft">같은 상품은 상태·옵션이 달라도 수량을 합쳐서 할인합니다.</p>
          </div>
        )}

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-semibold">상태 선택</legend>
          {product.skus.map((option) => {
            const soldOut = option.availableQty <= 0;
            const selected = option.id === skuId;
            const options = optionText(option.optionValues);
            return (
              <label
                key={option.id}
                className={`flex cursor-pointer flex-col gap-1.5 rounded-md border p-3 transition-colors ${
                  selected ? "border-ink bg-surface-muted" : "border-line hover:border-ink-faint"
                } ${soldOut ? "cursor-not-allowed opacity-50" : ""}`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="sku"
                    value={option.id}
                    checked={selected}
                    disabled={soldOut}
                    onChange={() => {
                      setSkuId(option.id);
                      setQty(Math.min(minOrderQty, Math.max(option.availableQty, 1)));
                      setImageIndex(0);
                    }}
                    className="accent-ink"
                  />
                  <GradeBadge grade={option.grade} />
                  {options && <span className="text-sm">{options}</span>}
                  <span className="ml-auto text-sm font-bold tabular-nums">
                    {formatWon(option.price)}
                  </span>
                </div>
                {option.businessPrice != null && option.businessPrice < option.price && (
                  <p className="pl-6 text-xs font-semibold text-navy">
                    사업자 회원가 {formatWon(option.businessPrice)}
                    <span className="font-normal text-ink-faint"> · 인증 회원은 장바구니에서 적용</span>
                  </p>
                )}
                {option.conditionNote && (
                  <p className="pl-6 text-xs text-ink-soft">{option.conditionNote}</p>
                )}
                <p className="pl-6 text-xs text-ink-faint">
                  {soldOut ? "품절" : option.availableQty <= 3 ? `${option.availableQty}개 남음` : "재고 있음"}
                </p>
              </label>
            );
          })}
        </fieldset>

        {sku && canBuy && (
          <div className="flex items-center justify-between rounded-md bg-surface-muted p-3">
            <div className="flex items-center rounded border border-line bg-surface">
              <button
                type="button"
                aria-label="수량 줄이기"
                onClick={() => changeQty(qty - 1)}
                className="size-9 text-lg disabled:opacity-30"
                disabled={qty <= minOrderQty}
              >
                −
              </button>
              <input
                type="number"
                inputMode="numeric"
                aria-label="수량"
                min={minOrderQty}
                max={maxQty}
                value={qty}
                onChange={(e) => changeQty(Number(e.target.value))}
                className="h-9 w-16 border-x border-line text-center tabular-nums [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
              />
              <button
                type="button"
                aria-label="수량 늘리기"
                onClick={() => changeQty(qty + 1)}
                className="size-9 text-lg disabled:opacity-30"
                disabled={qty >= maxQty}
              >
                +
              </button>
            </div>
            <p className="text-right text-sm">
              {bp > 0 && (
                <span className="block text-xs text-sale">
                  {formatRate(bp)} 할인 · 개당 {formatWon(unitPrice)}
                </span>
              )}
              합계 <strong className="text-lg tabular-nums">{formatWon(unitPrice * qty)}</strong>
            </p>
          </div>
        )}

        {canBuy ? (
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={addToCart}
              disabled={busy}
              className="h-12 rounded-md border border-brand font-semibold text-brand hover:bg-brand/5 disabled:opacity-50"
            >
              장바구니 담기
            </button>
            <button
              type="button"
              onClick={buyNow}
              disabled={busy}
              className="h-12 rounded-md bg-brand font-semibold text-white hover:bg-brand-strong disabled:opacity-50"
            >
              바로 구매
            </button>
          </div>
        ) : (
          <button type="button" disabled className="h-12 rounded-md bg-surface-muted font-semibold text-ink-faint">
            {maxQty > 0 ? `남은 수량(${maxQty}개)이 최소구매수량보다 적습니다` : "품절"}
          </button>
        )}
        {notice && (
          <p
            role="status"
            className={`flex items-center justify-between rounded-md p-3 text-sm ${
              notice.kind === "ok" ? "bg-brand/10 text-brand" : "bg-sale/10 text-sale"
            }`}
          >
            {notice.text}
            {notice.kind === "ok" && (
              <Link href="/cart" className="font-semibold underline underline-offset-4">
                장바구니 보기
              </Link>
            )}
          </p>
        )}
      </div>
    </div>
  );
}

/** 찜하기. 로그인 전이면 누를 때 로그인 화면으로 보낸다 */
function WishButton({ productId }: { productId: number }) {
  const router = useRouter();
  const [wished, setWished] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    communityApi
      .isWished(productId)
      .then((r) => setWished(r.wished))
      .catch(() => setWished(false));
  }, [productId]);

  async function toggle() {
    setBusy(true);
    try {
      if (wished) await communityApi.unwish(productId);
      else await communityApi.wish(productId);
      setWished(!wished);
    } catch (e) {
      if (e instanceof ClientApiError && e.status === 401) router.push(loginUrl());
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={busy}
      aria-pressed={wished}
      aria-label={wished ? "찜 해제" : "찜하기"}
      className={`flex size-11 shrink-0 items-center justify-center rounded-full border text-xl ${
        wished ? "border-sale text-sale" : "border-line text-ink-faint hover:text-sale"
      }`}
    >
      {wished ? "♥" : "♡"}
    </button>
  );
}
