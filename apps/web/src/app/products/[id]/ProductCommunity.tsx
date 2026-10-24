"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import {
  ClientApiError,
  communityApi,
  loginUrl,
  type Page,
  type ProductReviews,
  type Question,
} from "@/lib/client-api";

const dateFormat = new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" });

function Stars({ value, className = "" }: { value: number; className?: string }) {
  const rounded = Math.round(value);
  return (
    <span className={`tracking-tight ${className}`} aria-label={`별점 ${value}점`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} aria-hidden className={n <= rounded ? "text-tag" : "text-line"}>
          ★
        </span>
      ))}
    </span>
  );
}

/** 상품 상세 아래 구매후기·상품문의 */
export function ProductCommunity({ productId }: { productId: number }) {
  const [tab, setTab] = useState<"reviews" | "questions">("reviews");
  const [reviews, setReviews] = useState<ProductReviews | null>(null);

  useEffect(() => {
    communityApi
      .reviews(productId)
      .then(setReviews)
      .catch(() => setReviews(null));
  }, [productId]);

  return (
    <section className="flex flex-col gap-4">
      <div className="flex border-b border-ink" role="tablist">
        {(
          [
            ["reviews", `구매후기 ${reviews?.summary.count ?? 0}`],
            ["questions", "상품문의"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            onClick={() => setTab(value)}
            className={`-mb-px flex-1 border-b-2 py-2.5 text-sm sm:flex-none sm:px-8 ${
              tab === value ? "border-ink font-bold" : "border-transparent text-ink-soft"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "reviews" ? <ReviewList productId={productId} first={reviews} /> : <QuestionBoard productId={productId} />}
    </section>
  );
}

function ReviewList({ productId, first }: { productId: number; first: ProductReviews | null }) {
  const [page, setPage] = useState(0);
  // 첫 페이지는 위에서 받은 것을 그대로 쓰고, 다음 페이지부터 따로 불러온다
  const [later, setLater] = useState<ProductReviews | null>(null);
  const data = page === 0 ? first : later;

  useEffect(() => {
    if (page === 0) return;
    communityApi
      .reviews(productId, page)
      .then(setLater)
      .catch(() => undefined);
  }, [productId, page]);

  if (!data) return <p className="py-8 text-center text-sm text-ink-faint">후기를 불러오는 중입니다.</p>;
  const { summary } = data;
  if (summary.count === 0) {
    return (
      <p className="py-8 text-center text-sm text-ink-faint">
        아직 후기가 없습니다. 구매확정한 고객만 후기를 남길 수 있어요.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-6 rounded-md bg-surface-muted p-4">
        <div className="text-center">
          <p className="text-3xl font-bold tabular-nums">{summary.average.toFixed(1)}</p>
          <Stars value={summary.average} className="text-lg" />
        </div>
        <ul className="flex flex-1 flex-col gap-1 text-xs">
          {[5, 4, 3, 2, 1].map((star) => {
            const n = summary.counts[star - 1];
            return (
              <li key={star} className="flex items-center gap-2">
                <span className="w-6 text-ink-soft">{star}점</span>
                <span className="h-1.5 flex-1 overflow-hidden rounded bg-line">
                  <span className="block h-full bg-tag" style={{ width: `${(n / summary.count) * 100}%` }} />
                </span>
                <span className="w-6 text-right tabular-nums text-ink-faint">{n}</span>
              </li>
            );
          })}
        </ul>
      </div>
      <ul className="flex flex-col divide-y divide-line">
        {data.page.items.map((r) => (
          <li key={r.id} className="flex flex-col gap-1 py-4 text-sm">
            <div className="flex items-center gap-2">
              <Stars value={r.rating} />
              <span className="text-xs text-ink-faint">
                {r.author} · {dateFormat.format(new Date(r.createdAt))}
              </span>
            </div>
            {(r.grade || r.optionText) && (
              <p className="text-xs text-ink-faint">{[r.grade, r.optionText].filter(Boolean).join(" · ")}</p>
            )}
            <p className="leading-6 whitespace-pre-line">{r.content}</p>
          </li>
        ))}
      </ul>
      <Pager page={page} total={data.page.total} size={data.page.size} onChange={setPage} />
    </div>
  );
}

function QuestionBoard({ productId }: { productId: number }) {
  const router = useRouter();
  const [data, setData] = useState<Page<Question> | null>(null);
  const [page, setPage] = useState(0);
  const [writing, setWriting] = useState(false);
  const [question, setQuestion] = useState("");
  const [secret, setSecret] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      communityApi
        .questions(productId, page)
        .then(setData)
        .catch(() => setData(null)),
    [productId, page],
  );

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await communityApi.ask(productId, question.trim(), secret);
      setQuestion("");
      setSecret(false);
      setWriting(false);
      setPage(0);
      await load();
    } catch (err) {
      if (err instanceof ClientApiError && err.status === 401) {
        router.push(loginUrl());
        return;
      }
      setError(err instanceof ClientApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "등록하지 못했습니다.");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {writing ? (
        <form onSubmit={submit} className="flex flex-col gap-2 rounded-md border border-line p-3 text-sm">
          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            rows={3}
            minLength={5}
            maxLength={1000}
            required
            placeholder="대량 구매, 재입고, 상품 상태 등 궁금한 점을 남겨 주세요."
            className="rounded border border-line p-2"
          />
          <label className="flex items-center gap-2 text-xs text-ink-soft">
            <input type="checkbox" checked={secret} onChange={(e) => setSecret(e.target.checked)} />
            비밀글 (판매자와 나만 보기)
          </label>
          {error && <p className="text-xs text-sale">{error}</p>}
          <div className="flex gap-2">
            <button type="button" onClick={() => setWriting(false)} className="h-9 flex-1 rounded border border-line">
              취소
            </button>
            <button type="submit" className="h-9 flex-1 rounded bg-brand font-semibold text-white">
              문의 등록
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setWriting(true)}
          className="h-10 self-end rounded border border-ink px-4 text-sm font-semibold"
        >
          상품 문의하기
        </button>
      )}

      {data && data.items.length === 0 && <p className="py-6 text-center text-sm text-ink-faint">등록된 문의가 없습니다.</p>}
      <ul className="flex flex-col divide-y divide-line">
        {data?.items.map((q) => (
          <li key={q.id} className="flex flex-col gap-2 py-4 text-sm">
            <div className="flex items-center gap-2 text-xs">
              <span className={`rounded px-1.5 py-0.5 ${q.answeredAt ? "bg-brand/10 text-brand" : "bg-surface-muted text-ink-soft"}`}>
                {q.answeredAt ? "답변완료" : "답변대기"}
              </span>
              <span className="text-ink-faint">
                {q.author} · {dateFormat.format(new Date(q.createdAt))}
              </span>
            </div>
            <p className="whitespace-pre-line">
              {q.question ?? <span className="text-ink-faint">🔒 비밀글입니다.</span>}
            </p>
            {q.answer && (
              <div className="rounded bg-surface-muted p-3 text-sm">
                <p className="mb-1 text-xs font-semibold text-brand">판매자 답변</p>
                <p className="whitespace-pre-line">{q.answer}</p>
              </div>
            )}
          </li>
        ))}
      </ul>
      {data && <Pager page={page} total={data.total} size={data.size} onChange={setPage} />}
    </div>
  );
}

function Pager({ page, total, size, onChange }: { page: number; total: number; size: number; onChange: (p: number) => void }) {
  if (total <= size) return null;
  return (
    <div className="flex justify-center gap-2 text-sm">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)} className="rounded border border-line px-3 py-1 disabled:opacity-40">
        이전
      </button>
      <button
        type="button"
        disabled={(page + 1) * size >= total}
        onClick={() => onChange(page + 1)}
        className="rounded border border-line px-3 py-1 disabled:opacity-40"
      >
        다음
      </button>
    </div>
  );
}
