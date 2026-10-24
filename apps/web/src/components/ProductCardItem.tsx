import Link from "next/link";

import type { ProductCard } from "@/lib/api";
import { discountRate } from "@/lib/format";
import { GradeBadge } from "./GradeBadge";
import { PriceTag } from "./PriceTag";
import { ProductImage } from "./ProductImage";

/** 정가 대비 40% 이상 싸면 '땡처리' 가격표를 붙인다 */
const DDAENG_RATE = 40;

export function ProductCardItem({ product }: { product: ProductCard }) {
  const soldOut = product.availableQty <= 0;
  const rate = discountRate(product.minPrice, product.maxListPrice);
  return (
    <Link href={`/products/${product.id}`} className="group flex flex-col gap-2.5">
      <div className="relative overflow-hidden rounded-2xl ring-1 ring-line">
        <ProductImage
          src={product.thumbnailUrl}
          alt={product.name}
          className="aspect-square w-full transition-transform duration-300 group-hover:scale-[1.04]"
        />
        {rate !== null && rate >= DDAENG_RATE && !soldOut && (
          <span className="absolute top-2 left-2 -rotate-6 rounded-md bg-tag px-2 py-0.5 font-display text-sm text-[#c2185b] shadow">
            땡처리
          </span>
        )}
        {product.businessOnly && (
          <span className="absolute right-2 bottom-2 rounded bg-navy px-1.5 py-0.5 text-[11px] font-bold text-white">
            사업자 전용
          </span>
        )}
        {soldOut && (
          <div className="absolute inset-0 flex items-center justify-center bg-navy/55 font-display text-lg text-white">
            품절
          </div>
        )}
      </div>
      <div className="flex flex-wrap gap-1">
        {product.grades.map((grade) => (
          <GradeBadge key={grade.code} grade={grade} />
        ))}
      </div>
      <div className="flex flex-col gap-0.5">
        {product.brand && <span className="text-xs text-ink-faint">{product.brand}</span>}
        <h3 className="line-clamp-2 text-sm leading-snug text-ink group-hover:text-brand">{product.name}</h3>
      </div>
      <PriceTag
        price={product.minPrice}
        listPrice={product.maxListPrice}
        prefix={product.grades.length > 1 ? "~" : undefined}
      />
    </Link>
  );
}
