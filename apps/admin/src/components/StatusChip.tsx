import { PRODUCT_STATUS_LABEL, type ProductStatus } from "@/lib/api";

const styles: Record<ProductStatus, string> = {
  DRAFT: "bg-surface-muted text-ink-soft",
  ON_SALE: "bg-ok/10 text-ok",
  SOLD_OUT: "bg-danger/10 text-danger",
  HIDDEN: "bg-warn/10 text-warn",
  DELETED: "bg-surface-muted text-ink-faint line-through",
};

export function StatusChip({ status }: { status: ProductStatus }) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap ${styles[status]}`}>
      {PRODUCT_STATUS_LABEL[status]}
    </span>
  );
}
