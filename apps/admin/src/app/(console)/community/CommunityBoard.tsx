"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ConfirmButton } from "@/components/ConfirmButton";
import {
  ApiError,
  communityAdminApi,
  formatDate,
  type AdminQuestion,
  type AdminReview,
  type Page,
} from "@/lib/api";

const TABS = [
  { value: "pending", label: "답변 대기 문의" },
  { value: "questions", label: "전체 문의" },
  { value: "reviews", label: "구매후기" },
] as const;

type Tab = (typeof TABS)[number]["value"];

export function CommunityBoard() {
  const router = useRouter();
  const params = useSearchParams();
  const tab = (TABS.find((t) => t.value === params.get("tab"))?.value ?? "pending") as Tab;

  return (
    <div className="flex flex-col gap-4">
      <nav className="flex gap-1 border-b border-line" aria-label="문의·후기">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => router.push(`/community?tab=${t.value}`)}
            aria-current={tab === t.value ? "page" : undefined}
            className={`-mb-px border-b-2 px-4 py-2 text-sm ${
              tab === t.value ? "border-brand font-semibold text-ink" : "border-transparent text-ink-soft hover:text-ink"
            }`}
          >
            {t.label}
          </button>
        ))}
      </nav>
      {tab === "reviews" ? <ReviewTable /> : <QuestionList key={tab} unanswered={tab === "pending"} />}
    </div>
  );
}

function QuestionList({ unanswered }: { unanswered: boolean }) {
  const [data, setData] = useState<Page<AdminQuestion> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      communityAdminApi
        .questions(unanswered)
        .then(setData)
        .catch((e: Error) => setError(e.message)),
    [unanswered],
  );

  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>;
  if (!data) return <WhaleLoader />;
  if (data.items.length === 0) {
    return (
      <p className="card p-10 text-center text-sm text-ink-faint">
        {unanswered ? "답변을 기다리는 문의가 없습니다." : "등록된 문의가 없습니다."}
      </p>
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {data.items.map((q) => (
        <QuestionCard key={q.id} q={q} onAnswered={load} />
      ))}
    </ul>
  );
}

function QuestionCard({ q, onAnswered }: { q: AdminQuestion; onAnswered: () => void }) {
  const [answer, setAnswer] = useState(q.answer ?? "");
  const [editing, setEditing] = useState(!q.answer);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await communityAdminApi.answer(q.id, answer.trim());
      setEditing(false);
      onAnswered();
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="card flex flex-col gap-2 p-4 text-sm">
      <div className="flex flex-wrap items-center gap-2 text-xs text-ink-soft">
        <Link href={`/products/${q.productId}`} className="font-medium text-ink hover:underline">
          {q.productName}
        </Link>
        <span>· {q.author}</span>
        <span>· {formatDate(q.createdAt)}</span>
        {q.secret && <span className="rounded bg-surface-muted px-1.5 py-0.5">비밀글</span>}
        <span className="ml-auto">
          <ConfirmButton
            label="문의 삭제"
            question="광고·욕설 등으로 지울까요? 고객 화면에서도 사라집니다."
            className="text-xs text-danger underline underline-offset-2"
            onConfirm={async () => {
              try {
                await communityAdminApi.deleteQuestion(q.id);
                onAnswered();
              } catch (err) {
                setError(err instanceof ApiError ? err.message : "삭제하지 못했습니다.");
              }
            }}
          />
        </span>
      </div>
      <p className="whitespace-pre-line">{q.question}</p>
      {error && <p className="text-xs text-danger">{error}</p>}
      {editing ? (
        <form onSubmit={submit} className="flex flex-col gap-2">
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            rows={3}
            maxLength={2000}
            required
            placeholder="답변을 입력하세요. 고객에게 그대로 보입니다."
            className="input h-auto py-2"
          />
          <div className="flex justify-end gap-2">
            {q.answer && (
              <button type="button" className="btn-outline" onClick={() => setEditing(false)}>
                취소
              </button>
            )}
            <button type="submit" className="btn-primary" disabled={busy || !answer.trim()}>
              답변 등록
            </button>
          </div>
        </form>
      ) : (
        <div className="flex items-start justify-between gap-3 rounded bg-surface-muted p-3">
          <p className="whitespace-pre-line">
            <span className="mr-2 text-xs font-semibold text-brand">답변</span>
            {q.answer}
          </p>
          <button type="button" className="shrink-0 text-xs text-ink-soft underline" onClick={() => setEditing(true)}>
            고치기
          </button>
        </div>
      )}
    </li>
  );
}

function ReviewTable() {
  const [data, setData] = useState<Page<AdminReview> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    communityAdminApi
      .reviews()
      .then(setData)
      .catch((e: Error) => setError(e.message));
  }, []);

  async function toggle(r: AdminReview) {
    try {
      await communityAdminApi.setHidden(r.id, !r.hidden);
      setData((prev) => prev && { ...prev, items: prev.items.map((x) => (x.id === r.id ? { ...x, hidden: !r.hidden } : x)) });
    } catch (e) {
      setError(e instanceof Error ? e.message : "바꾸지 못했습니다.");
    }
  }

  if (error) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>;
  if (!data) return <WhaleLoader />;
  if (data.items.length === 0) return <p className="card p-10 text-center text-sm text-ink-faint">아직 구매후기가 없습니다.</p>;
  return (
    <ul className="card divide-y divide-line">
      {data.items.map((r) => (
        <li key={r.id} className={`flex gap-4 p-4 text-sm ${r.hidden ? "opacity-50" : ""}`}>
          <div className="flex flex-1 flex-col gap-1">
            <div className="flex flex-wrap items-center gap-2 text-xs text-ink-soft">
              <span className="text-warn" aria-label={`별점 ${r.rating}점`}>
                {"★".repeat(r.rating)}
                <span className="text-line">{"★".repeat(5 - r.rating)}</span>
              </span>
              <Link href={`/products/${r.productId}`} className="font-medium text-ink hover:underline">
                {r.productName}
              </Link>
              <span>· {r.author}</span>
              <span>· {formatDate(r.createdAt)}</span>
              {r.hidden && <span className="rounded bg-surface-muted px-1.5 py-0.5">숨김</span>}
            </div>
            <p className="whitespace-pre-line">{r.content}</p>
          </div>
          <button type="button" className="btn-outline h-8 shrink-0" onClick={() => toggle(r)}>
            {r.hidden ? "다시 보이기" : "숨기기"}
          </button>
        </li>
      ))}
    </ul>
  );
}
