"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import {
  draftToFields,
  emptyDraft,
  ProductFieldsEditor,
  type ProductDraft,
} from "@/components/ProductFieldsEditor";
import {
  emptySku,
  SkuDraftRow,
  skuDraftToRequest,
  validateSkuDraft,
  type SkuDraft,
} from "@/components/SkuDraftRow";
import { api, ApiError } from "@/lib/api";

export function NewProductForm() {
  const router = useRouter();
  const [draft, setDraft] = useState<ProductDraft>(emptyDraft);
  const [skus, setSkus] = useState<SkuDraft[]>(() => [emptySku()]);
  const [skuErrors, setSkuErrors] = useState<Record<number, Record<string, string>>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const localSkuErrors = Object.fromEntries(
      skus.map((s) => [s.key, validateSkuDraft(s)]).filter(([, e]) => Object.keys(e).length > 0),
    );
    const localErrors: Record<string, string> = {};
    if (!draft.name.trim()) localErrors["product.name"] = "상품명을 입력해 주세요.";
    setSkuErrors(localSkuErrors);
    setErrors(localErrors);
    if (Object.keys(localSkuErrors).length || Object.keys(localErrors).length) {
      setMessage("빨간 글씨로 표시된 항목을 확인해 주세요.");
      return;
    }

    setSaving(true);
    setMessage(null);
    try {
      const { id } = await api.createProduct({
        product: draftToFields(draft),
        skus: skus.map(skuDraftToRequest),
      });
      router.push(`/products/${id}?created=1`);
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(e.fieldErrors);
        setMessage(e.message);
      } else {
        setMessage("서버에 연결하지 못했습니다. API 서버가 실행 중인지 확인해 주세요.");
      }
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex max-w-4xl flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <Link href="/products" className="text-sm text-ink-faint hover:text-ink-soft">
            ← 상품 목록
          </Link>
          <h1 className="text-xl font-bold">상품 등록</h1>
        </div>
      </div>

      <section className="card flex flex-col gap-4 p-5">
        <h2 className="font-semibold">기본 정보</h2>
        <ProductFieldsEditor value={draft} onChange={setDraft} errors={errors} />
      </section>

      <section className="card flex flex-col gap-3 p-5">
        <div>
          <h2 className="font-semibold">판매 단위 (SKU)</h2>
          <p className="text-xs text-ink-faint">
            같은 상품이라도 상태등급이나 옵션이 다르면 SKU를 나눠 등록합니다. 초기 재고는 기본 창고에 입고됩니다.
          </p>
        </div>
        {skus.map((sku) => (
          <SkuDraftRow
            key={sku.key}
            value={sku}
            errors={skuErrors[sku.key]}
            onChange={(next) => setSkus((list) => list.map((s) => (s.key === sku.key ? next : s)))}
            onRemove={skus.length > 1 ? () => setSkus((list) => list.filter((s) => s.key !== sku.key)) : undefined}
          />
        ))}
        <button type="button" className="btn-outline self-start" onClick={() => setSkus((l) => [...l, emptySku()])}>
          + SKU 추가
        </button>
      </section>

      <div className="sticky bottom-0 -mx-4 flex items-center justify-end gap-3 border-t border-line bg-canvas/95 px-4 py-3 md:-mx-8 md:px-8">
        {message && (
          <p role="alert" className="mr-auto text-sm text-danger">
            {message}
          </p>
        )}
        <Link href="/products" className="btn-outline">
          취소
        </Link>
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? "저장 중…" : "준비중 상태로 저장"}
        </button>
      </div>
    </form>
  );
}
