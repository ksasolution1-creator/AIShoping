"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { formatDate, formatWon, inventoryApi, PURCHASE_STATUS_LABEL, type PurchaseDetail } from "@/lib/api";
import { openLabelPrint } from "@/lib/labels";
import { PurchaseEditor } from "./PurchaseEditor";
import { PURCHASE_STATUS_STYLE } from "./PurchaseList";

/** 매입서 상세: 작성 중이면 편집, 입고·취소된 건은 읽기 전용 */
export function PurchaseView({ id, justReceived }: { id: number; justReceived: boolean }) {
  const [purchase, setPurchase] = useState<PurchaseDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    inventoryApi
      .purchase(id)
      .then(setPurchase)
      .catch((e: Error) => setError(e.message));
  }, [id, justReceived]);

  if (error) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>;
  if (!purchase) return <WhaleLoader />;

  const header = (
    <div className="flex flex-wrap items-center gap-3">
      <Link href="/purchases" className="text-sm text-ink-soft hover:underline">
        ← 매입 목록
      </Link>
      <h1 className="font-mono text-lg font-bold">{purchase.purchaseNo}</h1>
      <span className={`rounded px-2 py-0.5 text-xs font-medium ${PURCHASE_STATUS_STYLE[purchase.status]}`}>
        {PURCHASE_STATUS_LABEL[purchase.status]}
      </span>
    </div>
  );

  if (purchase.status === "DRAFT") {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <PurchaseEditor key={purchase.id} initial={purchase} />
      </div>
    );
  }

  const totalQty = purchase.items.reduce((sum, i) => sum + i.qty, 0);
  return (
    <div className="flex flex-col gap-4">
      {header}
      {justReceived && purchase.status === "RECEIVED" && (
        <p role="status" className="rounded bg-ok/10 p-3 text-sm text-ok">
          입고를 마쳤습니다. 재고에 {totalQty.toLocaleString("ko-KR")}개가 더해졌습니다. 상품에 붙일 바코드 라벨을 인쇄하세요.
        </p>
      )}
      <dl className="card grid gap-3 p-4 text-sm sm:grid-cols-4">
        <Info label="매입처" value={purchase.supplierName ?? "-"} />
        <Info label="입고 위치" value={purchase.locationName} />
        <Info label="매입일" value={purchase.purchasedOn} />
        <Info label="입고 처리" value={purchase.receivedAt ? formatDate(purchase.receivedAt) : "-"} />
        {purchase.memo && <Info label="메모" value={purchase.memo} wide />}
      </dl>
      <div className="card overflow-x-auto">
        <table className="w-full min-w-[40rem] text-sm">
          <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
            <tr>
              <th className="px-3 py-2.5 font-medium">상품</th>
              <th className="px-3 py-2.5 text-right font-medium">수량</th>
              <th className="px-3 py-2.5 text-right font-medium">매입단가</th>
              <th className="px-3 py-2.5 text-right font-medium">금액</th>
            </tr>
          </thead>
          <tbody>
            {purchase.items.map((i) => (
              <tr key={i.skuId} className="border-b border-line last:border-0">
                <td className="px-3 py-2">
                  <div className="font-medium">{i.productName}</div>
                  <div className="text-xs text-ink-soft">
                    {i.grade.label}
                    {i.optionText && ` · ${i.optionText}`} · <span className="font-mono">{i.skuCode}</span>
                  </div>
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{i.qty.toLocaleString("ko-KR")}</td>
                <td className="px-3 py-2 text-right tabular-nums">{formatWon(i.unitCost)}</td>
                <td className="px-3 py-2 text-right tabular-nums">{formatWon(i.qty * i.unitCost)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot className="border-t-2 border-line font-semibold">
            <tr>
              <td className="px-3 py-2">합계 {purchase.items.length}종</td>
              <td className="px-3 py-2 text-right tabular-nums">{totalQty.toLocaleString("ko-KR")}</td>
              <td />
              <td className="px-3 py-2 text-right tabular-nums">{formatWon(purchase.totalCost)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      {purchase.status === "RECEIVED" && (
        <div className="flex justify-end">
          <button
            type="button"
            className="btn-primary"
            onClick={() =>
              openLabelPrint(
                purchase.items.map((i) => ({
                  skuCode: i.skuCode,
                  productName: i.productName,
                  optionText: i.optionText,
                  gradeLabel: i.grade.label,
                  price: i.price,
                  qty: i.qty,
                })),
              )
            }
          >
            입고 수량만큼 바코드 라벨 인쇄
          </button>
        </div>
      )}
    </div>
  );
}

function Info({ label, value, wide }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? "sm:col-span-4" : undefined}>
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className="mt-0.5">{value}</dd>
    </div>
  );
}
