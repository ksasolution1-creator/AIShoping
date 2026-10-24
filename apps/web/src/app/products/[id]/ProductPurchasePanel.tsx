"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";

import { GradeBadge } from "@/components/GradeBadge";
import { PriceTag } from "@/components/PriceTag";
import { ProductImage } from "@/components/ProductImage";
import type { ProductDetail } from "@/lib/api";
import { cartApi, ClientApiError, loginUrl } from "@/lib/client-api";
import { formatWon, optionText } from "@/lib/format";

export function ProductPurchasePanel({ product }: { product: ProductDetail }) {
  const router = useRouter();
  const firstAvailable = product.skus.find((s) => s.availableQty > 0) ?? product.skus[0];
  const [skuId, setSkuId] = useState(firstAvailable?.id);
  const [qty, setQty] = useState(1);
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
        <div className="flex flex-col gap-1.5">
          {product.brand && <span className="text-sm text-ink-faint">{product.brand}</span>}
          <h1 className="text-xl leading-snug font-bold sm:text-2xl">{product.name}</h1>
        </div>

        {sku && <PriceTag price={sku.price} listPrice={sku.listPrice} size="lg" />}

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
                      setQty(1);
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

        {sku && maxQty > 0 && (
          <div className="flex items-center justify-between rounded-md bg-surface-muted p-3">
            <div className="flex items-center rounded border border-line bg-surface">
              <button
                type="button"
                aria-label="수량 줄이기"
                onClick={() => setQty((q) => Math.max(1, q - 1))}
                className="size-9 text-lg disabled:opacity-30"
                disabled={qty <= 1}
              >
                −
              </button>
              <span className="w-10 text-center tabular-nums" aria-live="polite">
                {qty}
              </span>
              <button
                type="button"
                aria-label="수량 늘리기"
                onClick={() => setQty((q) => Math.min(maxQty, q + 1))}
                className="size-9 text-lg disabled:opacity-30"
                disabled={qty >= maxQty}
              >
                +
              </button>
            </div>
            <p className="text-sm">
              합계 <strong className="text-lg tabular-nums">{formatWon(sku.price * qty)}</strong>
            </p>
          </div>
        )}

        {maxQty > 0 ? (
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
            품절
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
