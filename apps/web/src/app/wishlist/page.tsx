"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ProductImage } from "@/components/ProductImage";
import { ClientApiError, communityApi, loginUrl, type WishItem } from "@/lib/client-api";
import { formatWon } from "@/lib/format";

export default function WishlistPage() {
  const router = useRouter();
  const [items, setItems] = useState<WishItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    communityApi
      .wishlist()
      .then(setItems)
      .catch((e) => {
        if (e instanceof ClientApiError && e.status === 401) router.replace(loginUrl());
        else setError("찜 목록을 불러오지 못했습니다.");
      });
  }, [router]);

  async function remove(productId: number) {
    await communityApi.unwish(productId).catch(() => undefined);
    setItems((prev) => prev?.filter((i) => i.productId !== productId) ?? null);
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 py-6">
      <h1 className="text-xl font-bold">찜한 상품</h1>
      {!items && (error ? <p className="text-sm text-sale">{error}</p> : <WhaleLoader label="찜 목록을 불러오는 중이에요" />)}
      {items && items.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-ink-faint">
          찜한 상품이 없습니다. 상품 상세에서 ♡를 눌러 담아 두세요.
          <Link href="/" className="text-brand underline underline-offset-4">
            상품 보러 가기
          </Link>
        </div>
      )}
      <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {items?.map((item) => {
          const soldOut = item.availableQty <= 0 || item.status !== "ON_SALE";
          return (
            <li key={item.productId} className="flex flex-col gap-2">
              <Link href={`/products/${item.productId}`} className="relative">
                <ProductImage src={item.thumbnailUrl} alt={item.name} className={`aspect-square w-full rounded-md ${soldOut ? "opacity-50" : ""}`} />
                {soldOut && (
                  <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-white">품절</span>
                )}
              </Link>
              <Link href={`/products/${item.productId}`} className="line-clamp-2 text-sm hover:underline">
                {item.name}
              </Link>
              <div className="flex items-center justify-between">
                <span className="font-bold tabular-nums">{item.minPrice != null ? `${formatWon(item.minPrice)}~` : "-"}</span>
                <button type="button" onClick={() => remove(item.productId)} className="text-xs text-ink-faint underline underline-offset-2">
                  삭제
                </button>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
