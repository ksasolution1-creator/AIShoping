import Link from "next/link";

import type { ProductCard } from "@/lib/api";
import { GradeBadge } from "./GradeBadge";
import { PriceTag } from "./PriceTag";
import { ProductImage } from "./ProductImage";

export function ProductCardItem({ product }: { product: ProductCard }) {
  const soldOut = product.availableQty <= 0;
  return (
    <Link href={`/products/${product.id}`} className="group flex flex-col gap-2.5">
      <div className="relative overflow-hidden rounded-md">
        <ProductImage
          src={product.thumbnailUrl}
          alt={product.name}
          className="aspect-square w-full transition-transform duration-300 group-hover:scale-[1.03]"
        />
        {soldOut && (
          <div className="absolute inset-0 flex items-center justify-center bg-ink/45 text-sm font-semibold text-white">
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
        {product.brand && (
          <span className="text-xs text-ink-faint">{product.brand}</span>
        )}
        <h3 className="line-clamp-2 text-sm leading-snug text-ink">
          {product.name}
        </h3>
      </div>
      <PriceTag
        price={product.minPrice}
        listPrice={product.maxListPrice}
        prefix={product.grades.length > 1 ? "~" : undefined}
      />
    </Link>
  );
}
