"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ApiError, businessAdminApi, formatDate, type BusinessApplication, type Page } from "@/lib/api";

const TABS = [
  { value: "PENDING", label: "심사 대기" },
  { value: "APPROVED", label: "승인" },
  { value: "REJECTED", label: "거절" },
  { value: "", label: "전체" },
];

const STATUS_LABEL: Record<BusinessApplication["status"], string> = {
  PENDING: "심사 대기",
  APPROVED: "승인",
  REJECTED: "거절",
};

const formatNo = (no: string) => no.replace(/(\d{3})(\d{2})(\d{5})/, "$1-$2-$3");

export function BusinessReview() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "PENDING";
  const [data, setData] = useState<Page<BusinessApplication> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      businessAdminApi
        .applications(status)
        .then(setData)
        .catch((e: Error) => setError(e.message)),
    [status],
  );

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="flex flex-col gap-4">
      <nav className="flex gap-1" aria-label="심사 상태">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => router.push(`/business-members?status=${t.value}`)}
            aria-current={status === t.value ? "page" : undefined}
            className={`rounded px-3 py-1.5 text-sm ${
              status === t.value ? "bg-ink font-semibold text-white" : "bg-surface text-ink-soft hover:bg-surface-muted"
            }`}
          >
            {t.label}
          </button>
        ))}
      </nav>
      <p className="text-xs text-ink-soft">
        승인 전에 홈택스 &lsquo;사업자등록 상태조회&rsquo;에서 휴·폐업 여부를 확인하세요. 같은 사업자등록번호는 한 회원만 승인할 수 있습니다.
      </p>
      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}
      {!data && !error && <WhaleLoader />}
      {data && data.items.length === 0 && <p className="card p-10 text-center text-sm text-ink-faint">해당하는 신청이 없습니다.</p>}
      <ul className="flex flex-col gap-3">
        {data?.items.map((a) => (
          <ApplicationCard key={a.userId} a={a} onDone={load} />
        ))}
      </ul>
    </div>
  );
}

function ApplicationCard({ a, onDone }: { a: BusinessApplication; onDone: () => void }) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function review(approve: boolean) {
    setBusy(true);
    setError(null);
    try {
      await businessAdminApi.review(a.userId, approve, approve ? null : reason.trim());
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "처리하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="card flex flex-col gap-3 p-4 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1">
          <dt className="text-ink-faint">상호</dt>
          <dd className="font-semibold">{a.businessName}</dd>
          <dt className="text-ink-faint">사업자번호</dt>
          <dd className="font-mono">{formatNo(a.businessNo)}</dd>
          <dt className="text-ink-faint">대표자</dt>
          <dd>{a.ceoName}</dd>
          {a.businessItem && (
            <>
              <dt className="text-ink-faint">업태·종목</dt>
              <dd>{a.businessItem}</dd>
            </>
          )}
          <dt className="text-ink-faint">회원</dt>
          <dd>
            {a.userName} <span className="text-ink-faint">{a.email}</span>
          </dd>
          <dt className="text-ink-faint">신청일</dt>
          <dd className="tabular-nums">{formatDate(a.createdAt)}</dd>
        </dl>
        <span className="rounded bg-surface-muted px-2 py-0.5 text-xs font-medium">{STATUS_LABEL[a.status]}</span>
      </div>
      {a.rejectReason && <p className="text-xs text-danger">거절 사유: {a.rejectReason}</p>}
      {error && <p className="text-xs text-danger">{error}</p>}
      {a.status === "PENDING" &&
        (rejecting ? (
          <div className="flex flex-wrap gap-2">
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={200}
              autoFocus
              placeholder="거절 사유 (고객에게 보입니다)"
              className="input min-w-60 flex-1"
            />
            <button type="button" className="btn-outline" onClick={() => setRejecting(false)}>
              취소
            </button>
            <button type="button" className="btn-outline text-danger" disabled={busy || !reason.trim()} onClick={() => review(false)}>
              거절
            </button>
          </div>
        ) : (
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-outline" onClick={() => setRejecting(true)}>
              거절…
            </button>
            <button type="button" className="btn-primary" disabled={busy} onClick={() => review(true)}>
              승인
            </button>
          </div>
        ))}
    </li>
  );
}
