import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { ApiError, fetchProduct } from "@/lib/api";
import { formatWon } from "@/lib/format";
import { ProductCommunity } from "./ProductCommunity";
import { ProductPurchasePanel } from "./ProductPurchasePanel";

type Params = Promise<{ id: string }>;

async function loadProduct(params: Params) {
  const id = Number((await params).id);
  if (!Number.isInteger(id) || id <= 0) notFound();
  try {
    return await fetchProduct(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const product = await loadProduct(params);
  return { title: product.name };
}

export default function ProductPage({ params }: { params: Params }) {
  return (
    <div className="pt-4">
      <Suspense fallback={<DetailSkeleton />}>
        <ProductDetail params={params} />
      </Suspense>
    </div>
  );
}

async function ProductDetail({ params }: { params: Params }) {
  const product = await loadProduct(params);
  const notice = Object.entries(product.noticeInfo);
  const { spec, returnInfo } = product;

  return (
    <div className="flex flex-col gap-12">
      <nav aria-label="카테고리 경로" className="-mb-8 flex flex-wrap items-center gap-1 text-sm text-ink-faint">
        <Link href="/" className="hover:text-ink-soft">
          홈
        </Link>
        {product.categoryPath.map((c) => (
          <span key={c.id} className="flex items-center gap-1">
            <span aria-hidden>›</span>
            <Link href={`/?categoryId=${c.id}`} className="hover:text-ink-soft">
              {c.name}
            </Link>
          </span>
        ))}
      </nav>

      <ProductPurchasePanel product={product} />

      <section className="flex flex-col gap-3">
        <h2 className="border-b border-ink pb-2 text-base font-bold">상품 설명</h2>
        <p className="text-sm leading-7 whitespace-pre-line text-ink-soft">
          {product.description || "등록된 설명이 없습니다."}
        </p>
      </section>

      <ProductCommunity productId={product.id} />

      <section className="flex flex-col gap-3">
        <h2 className="border-b border-ink pb-2 text-base font-bold">배송 안내</h2>
        <dl className="grid grid-cols-[7rem_1fr] gap-y-2 text-sm">
          <dt className="text-ink-faint">배송비</dt>
          <dd>
            {product.shippingFee === 0 ? "무료배송" : formatWon(product.shippingFee)}
            {product.freeShippingThreshold && product.shippingFee > 0 && (
              <span className="text-ink-faint">
                {" "}
                ({formatWon(product.freeShippingThreshold)} 이상 구매 시 무료)
              </span>
            )}
          </dd>
          <dt className="text-ink-faint">청약철회</dt>
          <dd>
            수령 후 7일 이내 가능합니다. 상품 설명에 미리 적힌 하자는 판매자 귀책 반품에서 제외되며, 이 경우
            단순변심 반품(반품 배송비 고객 부담)으로 처리됩니다.
          </dd>
        </dl>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="border-b border-ink pb-2 text-base font-bold">반품·교환 안내</h2>
        <dl className="grid grid-cols-[7rem_1fr] gap-y-2 text-sm">
          <dt className="text-ink-faint">반품 배송비</dt>
          <dd>
            단순 변심은 편도 {formatWon(returnInfo.oneWayFee)}
            <span className="text-ink-faint"> (무료배송으로 받은 주문은 왕복 {formatWon(returnInfo.oneWayFee * 2)})</span>
            <span className="block text-ink-faint">상품 불량·오배송 등 판매자 사유는 반품 배송비가 없습니다.</span>
          </dd>
          <dt className="text-ink-faint">신청 기간</dt>
          <dd>구매확정 전까지 주문 상세에서 신청할 수 있습니다. 배송완료 8일 뒤 자동으로 구매확정됩니다.</dd>
          {returnInfo.returnAddress && (
            <>
              <dt className="text-ink-faint">반품지</dt>
              <dd>
                {returnInfo.returnZipcode && `[${returnInfo.returnZipcode}] `}
                {returnInfo.returnAddress}
              </dd>
            </>
          )}
        </dl>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="border-b border-ink pb-2 text-base font-bold">상품정보 제공고시</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[20rem] text-sm">
            <tbody>
              {spec.origin && <NoticeRow label="원산지" value={spec.origin} />}
              {spec.manufacturer && <NoticeRow label="제조사·수입원" value={spec.manufacturer} />}
              {(spec.packageSize || spec.packageWeightG != null) && (
                <NoticeRow
                  label="포장 부피·무게"
                  value={[spec.packageSize, spec.packageWeightG != null ? `${(spec.packageWeightG / 1000).toLocaleString("ko-KR")}kg` : null]
                    .filter(Boolean)
                    .join(" / ")}
                />
              )}
              {product.brand && <NoticeRow label="브랜드" value={product.brand} />}
              {product.modelName && <NoticeRow label="모델명" value={product.modelName} />}
              {product.kcCertNo && <NoticeRow label="KC 인증번호" value={product.kcCertNo} />}
              {notice.map(([label, value]) => (
                <NoticeRow key={label} label={label} value={value} />
              ))}
              {notice.length === 0 && !product.brand && (
                <NoticeRow label="안내" value="상품 상세 참조" />
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function NoticeRow({ label, value }: { label: string; value: string }) {
  return (
    <tr className="border-b border-line">
      <th scope="row" className="w-36 bg-surface-muted px-3 py-2.5 text-left font-medium text-ink-soft">
        {label}
      </th>
      <td className="px-3 py-2.5">{value}</td>
    </tr>
  );
}

function DetailSkeleton() {
  return (
    <div className="grid gap-8 pt-4 md:grid-cols-2" aria-hidden>
      <div className="aspect-square animate-pulse rounded-md bg-surface-muted" />
      <div className="flex flex-col gap-3">
        <div className="h-4 w-24 animate-pulse rounded bg-surface-muted" />
        <div className="h-7 w-3/4 animate-pulse rounded bg-surface-muted" />
        <div className="h-24 animate-pulse rounded bg-surface-muted" />
      </div>
    </div>
  );
}
