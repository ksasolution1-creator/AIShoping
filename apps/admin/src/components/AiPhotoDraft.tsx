"use client";

import { useEffect, useRef, useState } from "react";

import { aiApi, ApiError, type AiDraft } from "@/lib/api";

/** 사진 한 장. blob은 줄인 JPEG (자동 입력과 상품 사진 등록에 같이 쓴다) */
export type DraftPhoto = { key: number; blob: Blob; previewUrl: string };

// Claude가 이미지를 볼 때 긴 변 1568px까지만 쓰므로 그 크기로 줄여 보낸다 (업로드도 빨라진다)
const MAX_EDGE = 1568;
let nextKey = 1;

async function shrink(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("사진을 줄이지 못했습니다."))), "image/jpeg", 0.88),
  );
}

/**
 * 상품 등록 화면 맨 위: 사진을 고르면 Claude가 상품명·카테고리·상태등급·설명을 채운다.
 * 고른 사진은 저장할 때 상품 사진으로도 올린다.
 */
export function AiPhotoDraft({
  photos,
  onPhotosChange,
  onDraft,
}: {
  photos: DraftPhoto[];
  onPhotosChange: (photos: DraftPhoto[]) => void;
  onDraft: (draft: AiDraft) => void;
}) {
  const [config, setConfig] = useState<{ enabled: boolean; maxPhotos: number } | null>(null);
  const [hint, setHint] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const photosRef = useRef(photos);
  useEffect(() => {
    photosRef.current = photos;
  }, [photos]);

  useEffect(() => {
    aiApi
      .config()
      .then(setConfig)
      .catch(() => setConfig({ enabled: false, maxPhotos: 6 }));
  }, []);

  // 화면을 떠날 때 미리보기 주소를 정리한다
  useEffect(() => () => photosRef.current.forEach((p) => URL.revokeObjectURL(p.previewUrl)), []);

  const maxPhotos = config?.maxPhotos ?? 6;

  async function addFiles(files: FileList | null) {
    if (!files?.length) return;
    setMessage(null);
    const room = maxPhotos - photos.length;
    const picked = Array.from(files).filter((f) => f.type.startsWith("image/")).slice(0, room);
    try {
      const added = await Promise.all(
        picked.map(async (file) => {
          const blob = await shrink(file);
          return { key: nextKey++, blob, previewUrl: URL.createObjectURL(blob) };
        }),
      );
      onPhotosChange([...photos, ...added]);
      if (files.length > room) setMessage({ kind: "error", text: `사진은 ${maxPhotos}장까지 올릴 수 있습니다.` });
    } catch {
      setMessage({ kind: "error", text: "사진을 읽지 못했습니다. JPG·PNG 사진으로 다시 골라 주세요." });
    }
    if (inputRef.current) inputRef.current.value = "";
  }

  function remove(key: number) {
    const target = photos.find((p) => p.key === key);
    if (target) URL.revokeObjectURL(target.previewUrl);
    onPhotosChange(photos.filter((p) => p.key !== key));
  }

  async function analyze() {
    setBusy(true);
    setMessage(null);
    setWarnings([]);
    try {
      const draft = await aiApi.draft(
        photos.map((p) => p.blob),
        hint,
      );
      onDraft(draft);
      setWarnings(draft.warnings);
      setMessage({
        kind: "ok",
        text: "사진을 보고 아래 칸을 채웠습니다. 판매가는 직접 넣고, 내용이 맞는지 확인한 뒤 저장하세요.",
      });
    } catch (e) {
      setMessage({ kind: "error", text: e instanceof ApiError ? e.message : "사진 분석에 실패했습니다." });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card flex flex-col gap-3 border-brand/30 p-5">
      <div>
        <h2 className="font-semibold">사진으로 자동 입력</h2>
        <p className="text-xs text-ink-faint">
          상품 앞·뒤, 라벨(모델명·제조국), 흠집 부위를 찍어 올리면 상품명·카테고리·상태등급·설명을 채워 드립니다. 고른
          사진은 저장할 때 상품 사진으로도 등록됩니다.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {photos.map((p, i) => (
          <div key={p.key} className="relative size-20 overflow-hidden rounded border border-line bg-surface-muted">
            {/* eslint-disable-next-line @next/next/no-img-element -- 로컬 미리보기(blob:) */}
            <img src={p.previewUrl} alt={`사진 ${i + 1}`} className="size-full object-cover" />
            {i === 0 && (
              <span className="absolute bottom-0 left-0 bg-ink/70 px-1 text-[10px] text-white">대표</span>
            )}
            <button
              type="button"
              onClick={() => remove(p.key)}
              aria-label={`사진 ${i + 1} 빼기`}
              className="absolute top-0.5 right-0.5 flex size-5 items-center justify-center rounded-full bg-ink/70 text-xs text-white"
            >
              ×
            </button>
          </div>
        ))}
        {photos.length < maxPhotos && (
          <label className="flex size-20 cursor-pointer flex-col items-center justify-center rounded border border-dashed border-line text-xs text-ink-soft hover:bg-surface-muted">
            <span className="text-xl leading-none">+</span>
            사진 추가
            <input
              ref={inputRef}
              type="file"
              accept="image/*"
              multiple
              className="sr-only"
              onChange={(e) => void addFiles(e.target.files)}
            />
          </label>
        )}
      </div>

      <label className="flex flex-col gap-1">
        <span className="field-label">메모 (선택) - 사진으로 알 수 없는 내용</span>
        <input
          className="input"
          value={hint}
          maxLength={300}
          onChange={(e) => setHint(e.target.value)}
          placeholder="예: 반품 회수품, 1회 사용, 충전기 없음, 정가 39,000원"
        />
      </label>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="btn-primary"
          disabled={busy || photos.length === 0 || !config?.enabled}
          onClick={analyze}
        >
          {busy ? "사진 분석 중… (10~30초)" : "사진으로 채우기"}
        </button>
        {config && !config.enabled && (
          <span className="text-xs text-warn">자동 입력이 꺼져 있습니다 (서버에 ANTHROPIC_API_KEY 설정 필요). 사진 등록은 됩니다.</span>
        )}
      </div>

      {message && (
        <p role={message.kind === "error" ? "alert" : "status"} className={`text-sm ${message.kind === "ok" ? "text-ok" : "text-danger"}`}>
          {message.text}
        </p>
      )}
      {warnings.length > 0 && (
        <div className="rounded bg-warn/10 p-3 text-sm">
          <p className="mb-1 font-semibold text-warn">저장 전에 확인하세요</p>
          <ul className="list-disc pl-5 text-ink-soft">
            {warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
