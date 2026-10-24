"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { draftFromProduct, draftToFields, ProductFieldsEditor, type ProductDraft } from "@/components/ProductFieldsEditor";
import { emptySku, SkuDraftRow, skuDraftToRequest, validateSkuDraft, type SkuDraft } from "@/components/SkuDraftRow";
import { StatusChip } from "@/components/StatusChip";
import { Thumb } from "@/components/Thumb";
import {
  api,
  ApiError,
  formatDate,
  formatWon,
  optionsToText,
  parseOptions,
  SKU_STATUS_LABEL,
  type AdminProductDetail,
  type AdminSku,
  type ProductStatus,
  type SkuStatus,
} from "@/lib/api";

const SHOP_URL = process.env.NEXT_PUBLIC_SHOP_URL ?? "http://localhost:3000";

const STATUS_ACTIONS: { status: ProductStatus; label: string }[] = [
  { status: "ON_SALE", label: "판매 시작" },
  { status: "SOLD_OUT", label: "품절 처리" },
  { status: "HIDDEN", label: "숨기기" },
  { status: "DRAFT", label: "준비중으로" },
];

function errorMessage(e: unknown) {
  return e instanceof ApiError ? e.message : "서버에 연결하지 못했습니다.";
}

/** 상품 상세·수정 화면. id와 created는 서버 페이지(page.tsx)가 주소에서 읽어 넘긴다 */
export function ProductEditor({ id, created }: { id: number; created: boolean }) {
  const [product, setProduct] = useState<AdminProductDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(
    created ? { kind: "ok", text: "상품을 등록했습니다. 사진을 올리고 판매를 시작하세요." } : null,
  );

  const reload = useCallback(async () => {
    try {
      setProduct(await api.product(id));
    } catch (e) {
      setLoadError(errorMessage(e));
    }
  }, [id]);

  useEffect(() => {
    // 첫 진입 시 한 번 불러온다
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload();
  }, [reload]);

  if (loadError) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{loadError}</p>;
  if (!product) return <WhaleLoader />;

  const show = (kind: "ok" | "error", text: string) => setNotice({ kind, text });

  async function changeStatus(status: ProductStatus) {
    try {
      setProduct(await api.changeStatus(id, status));
      show("ok", "상태를 바꿨습니다.");
    } catch (e) {
      show("error", errorMessage(e));
    }
  }

  return (
    <div className="flex max-w-5xl flex-col gap-6">
      <div className="flex flex-col gap-3">
        <Link href="/products" className="text-sm text-ink-faint hover:text-ink-soft">
          ← 상품 목록
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-xl font-bold">{product.name}</h1>
          <StatusChip status={product.status} />
          <span className="text-xs text-ink-faint">
            #{product.id} · 수정 {formatDate(product.updatedAt)}
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          {STATUS_ACTIONS.filter((a) => a.status !== product.status).map((action) => (
            <button
              key={action.status}
              type="button"
              className={action.status === "ON_SALE" ? "btn-primary" : "btn-outline"}
              onClick={() => changeStatus(action.status)}
            >
              {action.label}
            </button>
          ))}
          {(product.status === "ON_SALE" || product.status === "SOLD_OUT") && (
            <a href={`${SHOP_URL}/products/${product.id}`} target="_blank" rel="noreferrer" className="btn-outline">
              쇼핑몰에서 보기 ↗
            </a>
          )}
        </div>
        {notice && (
          <p
            role="status"
            className={`rounded p-3 text-sm ${notice.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
          >
            {notice.text}
          </p>
        )}
      </div>

      <SkuSection product={product} onChanged={reload} show={show} />
      <ImageSection product={product} onChanged={reload} show={show} />
      <BasicInfoSection product={product} onSaved={setProduct} show={show} />
    </div>
  );
}

type SectionProps = {
  product: AdminProductDetail;
  show: (kind: "ok" | "error", text: string) => void;
};

// ----------------------------------------------------------------------
// SKU·재고
// ----------------------------------------------------------------------

function SkuSection({ product, onChanged, show }: SectionProps & { onChanged: () => Promise<void> }) {
  const [adding, setAdding] = useState<SkuDraft | null>(null);
  const [addErrors, setAddErrors] = useState<Record<string, string>>({});

  async function addSku() {
    if (!adding) return;
    const errors = validateSkuDraft(adding);
    setAddErrors(errors);
    if (Object.keys(errors).length) return;
    try {
      await api.addSku(product.id, skuDraftToRequest(adding));
      setAdding(null);
      await onChanged();
      show("ok", "SKU를 추가했습니다.");
    } catch (e) {
      show("error", errorMessage(e));
    }
  }

  return (
    <section className="card flex flex-col gap-3 p-5">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">판매 단위 (SKU)·재고</h2>
        {!adding && (
          <button type="button" className="btn-outline" onClick={() => setAdding(emptySku())}>
            + SKU 추가
          </button>
        )}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[60rem] text-sm">
          <thead className="border-b border-line text-left text-xs text-ink-soft">
            <tr>
              <th className="py-2 pr-3 font-medium">상태등급 / SKU 코드</th>
              <th className="py-2 pr-3 font-medium">옵션 · 상태 설명 · 바코드</th>
              <th className="py-2 pr-3 font-medium">판매가 / 정가</th>
              <th className="py-2 pr-3 font-medium">판매</th>
              <th className="py-2 pr-3 text-right font-medium">재고 (예약)</th>
              <th className="py-2 font-medium">재고 조정</th>
            </tr>
          </thead>
          <tbody>
            {product.skus.map((sku) => (
              <SkuRow key={sku.id} sku={sku} onChanged={onChanged} show={show} />
            ))}
          </tbody>
        </table>
      </div>
      {adding && (
        <div className="flex flex-col gap-2 rounded bg-canvas p-3">
          <SkuDraftRow value={adding} onChange={setAdding} errors={addErrors} />
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-outline" onClick={() => setAdding(null)}>
              취소
            </button>
            <button type="button" className="btn-primary" onClick={addSku}>
              SKU 저장
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function SkuRow({ sku, onChanged, show }: { sku: AdminSku } & Pick<SectionProps, "show"> & { onChanged: () => Promise<void> }) {
  const [options, setOptions] = useState(optionsToText(sku.optionValues));
  const [note, setNote] = useState(sku.conditionNote ?? "");
  const [barcode, setBarcode] = useState(sku.barcode ?? "");
  const [price, setPrice] = useState(String(sku.price));
  const [listPrice, setListPrice] = useState(sku.listPrice == null ? "" : String(sku.listPrice));
  const [status, setStatus] = useState<SkuStatus>(sku.status);
  const [qty, setQty] = useState("");
  const [memo, setMemo] = useState("");

  const dirty =
    options !== optionsToText(sku.optionValues) ||
    note !== (sku.conditionNote ?? "") ||
    barcode !== (sku.barcode ?? "") ||
    price !== String(sku.price) ||
    listPrice !== (sku.listPrice == null ? "" : String(sku.listPrice)) ||
    status !== sku.status;

  async function save() {
    try {
      await api.updateSku(sku.id, {
        optionValues: parseOptions(options),
        conditionNote: note.trim() || null,
        barcode: barcode.trim() || null,
        price: Number(price),
        listPrice: listPrice ? Number(listPrice) : null,
        status,
      });
      await onChanged();
      show("ok", `${sku.skuCode} 저장했습니다.`);
    } catch (e) {
      show("error", errorMessage(e));
    }
  }

  async function adjust() {
    const n = Number(qty);
    if (!Number.isInteger(n) || n === 0) {
      show("error", "재고 조정 수량은 0이 아닌 정수로 입력해 주세요. 줄일 때는 -2처럼 입력합니다.");
      return;
    }
    try {
      await api.adjustStock(sku.id, { qty: n, memo: memo.trim() || null });
      setQty("");
      setMemo("");
      await onChanged();
      show("ok", `${sku.skuCode} 재고를 ${n > 0 ? `${n}개 늘렸습니다` : `${-n}개 줄였습니다`}.`);
    } catch (e) {
      show("error", errorMessage(e));
    }
  }

  const available = sku.onHand - sku.reserved;

  return (
    <tr className="border-b border-line align-top last:border-0">
      <td className="py-2.5 pr-3">
        <div className="font-medium">{sku.grade.label}</div>
        <div className="font-mono text-xs text-ink-faint">{sku.skuCode}</div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex flex-col gap-1">
          <input aria-label="옵션" className="input h-8" value={options} placeholder="옵션 없음" onChange={(e) => setOptions(e.target.value)} />
          <input aria-label="상태 설명" className="input h-8" value={note} placeholder="상태 설명" onChange={(e) => setNote(e.target.value)} />
          <input aria-label="바코드" className="input h-8" value={barcode} placeholder="바코드" onChange={(e) => setBarcode(e.target.value)} />
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex w-28 flex-col gap-1">
          <input aria-label="판매가" type="number" min={0} className="input h-8" value={price} onChange={(e) => setPrice(e.target.value)} />
          <input aria-label="정가" type="number" min={0} className="input h-8" value={listPrice} placeholder="정가" onChange={(e) => setListPrice(e.target.value)} />
          <span className="text-[11px] text-ink-faint">{formatWon(Number(price) || 0)}</span>
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex flex-col gap-1">
          <select aria-label="판매 상태" className="input h-8 w-20" value={status} onChange={(e) => setStatus(e.target.value as SkuStatus)}>
            {(["ACTIVE", "INACTIVE"] as SkuStatus[]).map((s) => (
              <option key={s} value={s}>
                {SKU_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
          <button type="button" className="btn-primary h-8 px-2.5" disabled={!dirty} onClick={save}>
            저장
          </button>
        </div>
      </td>
      <td className="py-2.5 pr-3 text-right tabular-nums">
        <div className={available <= 0 ? "font-semibold text-danger" : "font-semibold"}>{sku.onHand}</div>
        {sku.reserved > 0 && <div className="text-xs text-ink-faint">예약 {sku.reserved}</div>}
        <div className="text-xs text-ink-faint">판매가능 {available}</div>
      </td>
      <td className="py-2.5">
        <div className="flex w-44 flex-col gap-1">
          <input aria-label="조정 수량" type="number" className="input h-8" value={qty} placeholder="+3 또는 -1" onChange={(e) => setQty(e.target.value)} />
          <input aria-label="조정 사유" className="input h-8" value={memo} placeholder="사유 (예: 파손, 추가 입고)" onChange={(e) => setMemo(e.target.value)} />
          <button type="button" className="btn-outline h-8 px-2.5" disabled={!qty} onClick={adjust}>
            재고 반영
          </button>
        </div>
      </td>
    </tr>
  );
}

// ----------------------------------------------------------------------
// 사진
// ----------------------------------------------------------------------

function ImageSection({ product, onChanged, show }: SectionProps & { onChanged: () => Promise<void> }) {
  const [uploading, setUploading] = useState<string | null>(null);
  const groups = [
    { key: "common", skuId: null as number | null, title: "상품 공통 사진", hint: "목록 대표 사진으로 쓰입니다." },
    ...product.skus.map((s) => ({
      key: `sku-${s.id}`,
      skuId: s.id as number | null,
      title: `${s.grade.label} 실물 사진`,
      hint: [optionsToText(s.optionValues), s.skuCode].filter(Boolean).join(" · "),
    })),
  ];

  async function upload(skuId: number | null, key: string, files: FileList | null) {
    if (!files?.length) return;
    setUploading(key);
    try {
      for (const file of Array.from(files)) {
        await api.uploadImage(product.id, file, skuId);
      }
      await onChanged();
      show("ok", `사진 ${files.length}장을 올렸습니다.`);
    } catch (e) {
      show("error", errorMessage(e));
    } finally {
      setUploading(null);
    }
  }

  async function remove(imageId: number) {
    try {
      await api.deleteImage(product.id, imageId);
      await onChanged();
      show("ok", "사진을 삭제했습니다.");
    } catch (e) {
      show("error", errorMessage(e));
    }
  }

  return (
    <section className="card flex flex-col gap-4 p-5">
      <div>
        <h2 className="font-semibold">사진</h2>
        <p className="text-xs text-ink-faint">
          반품·리퍼·중고 상품은 상태등급별 실물 사진을 올려 주세요. 쇼핑몰에서 해당 상태를 고르면 그 사진이 먼저 보입니다.
        </p>
      </div>
      {groups.map((group) => {
        const images = product.images.filter((i) => i.skuId === group.skuId);
        const inputId = `upload-${group.key}`;
        return (
          <div key={group.key} className="flex flex-col gap-2 border-t border-line pt-3 first:border-0 first:pt-0">
            <div className="flex flex-wrap items-baseline gap-2">
              <h3 className="text-sm font-medium">{group.title}</h3>
              <span className="text-xs text-ink-faint">{group.hint}</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {images.map((image) => (
                <div key={image.id} className="relative">
                  <Thumb src={image.url} className="size-24" />
                  <button
                    type="button"
                    onClick={() => remove(image.id)}
                    aria-label="사진 삭제"
                    className="absolute top-1 right-1 flex size-6 items-center justify-center rounded-full bg-ink/70 text-xs text-white hover:bg-danger"
                  >
                    ×
                  </button>
                </div>
              ))}
              <label
                htmlFor={inputId}
                className="flex size-24 cursor-pointer flex-col items-center justify-center rounded border border-dashed border-ink-faint text-xs text-ink-soft hover:bg-surface-muted"
              >
                {uploading === group.key ? "올리는 중…" : "+ 사진 추가"}
              </label>
              <input
                id={inputId}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                multiple
                className="sr-only"
                disabled={uploading !== null}
                onChange={(e) => {
                  void upload(group.skuId, group.key, e.target.files);
                  e.target.value = "";
                }}
              />
            </div>
          </div>
        );
      })}
    </section>
  );
}

// ----------------------------------------------------------------------
// 기본 정보
// ----------------------------------------------------------------------

function BasicInfoSection({ product, onSaved, show }: SectionProps & { onSaved: (p: AdminProductDetail) => void }) {
  const [draft, setDraft] = useState<ProductDraft>(() => draftFromProduct(product));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      onSaved(await api.updateProduct(product.id, draftToFields(draft)));
      setErrors({});
      show("ok", "기본 정보를 저장했습니다.");
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.fieldErrors);
      show("error", errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">기본 정보</h2>
        <button type="button" className="btn-primary" disabled={saving} onClick={save}>
          {saving ? "저장 중…" : "기본 정보 저장"}
        </button>
      </div>
      <ProductFieldsEditor value={draft} onChange={setDraft} errors={errors} />
    </section>
  );
}
