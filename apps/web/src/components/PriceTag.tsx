import { discountRate, formatWon } from "@/lib/format";

export function PriceTag({
  price,
  listPrice,
  prefix,
  size = "sm",
}: {
  price: number;
  listPrice: number | null;
  prefix?: string;
  size?: "sm" | "lg";
}) {
  const rate = discountRate(price, listPrice);
  const priceClass = size === "lg" ? "text-2xl" : "text-base";
  return (
    <div className="flex flex-col gap-0.5">
      {rate !== null && listPrice && (
        <span className="text-xs text-ink-faint line-through tabular-nums">
          {formatWon(listPrice)}
        </span>
      )}
      <div className={`flex items-baseline gap-1.5 font-bold ${priceClass}`}>
        {rate !== null && <span className="text-sale tabular-nums">{rate}%</span>}
        <span className="tabular-nums">
          {formatWon(price)}
          {prefix && (
            <span className="ml-0.5 text-xs font-normal text-ink-faint">
              {prefix}
            </span>
          )}
        </span>
      </div>
    </div>
  );
}
