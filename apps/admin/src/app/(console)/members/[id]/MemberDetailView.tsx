"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  ApiError,
  BUSINESS_STATUS_LABEL,
  formatDate,
  formatWon,
  JOIN_METHOD_LABEL,
  memberApi,
  type MemberDetail,
} from "@/lib/api";
import { StatusBadge } from "../MemberList";

const AGREEMENT_LABEL: Record<MemberDetail["agreements"][number]["type"], string> = {
  TERMS: "이용약관",
  PRIVACY: "개인정보 수집·이용",
  MARKETING: "마케팅 수신",
  AGE_14: "만 14세 이상",
};

const ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: "입금대기",
  PAID: "결제완료",
  COMPLETED: "완료",
  CANCELLED: "취소",
};

export function MemberDetailView({ id }: { id: number }) {
  const [member, setMember] = useState<MemberDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    memberApi
      .detail(id)
      .then(setMember)
      .catch((e: Error) => setError(e.message));
  }, [id]);

  if (error && !member) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>;
  if (!member) return <WhaleLoader />;

  return (
    <div className="flex max-w-4xl flex-col gap-5">
      <div>
        <Link href="/members" className="text-sm text-ink-faint hover:text-ink-soft">
          ← 회원 목록
        </Link>
        <h1 className="flex flex-wrap items-center gap-2 text-xl font-bold">
          {member.name} <StatusBadge status={member.status} />
        </h1>
      </div>

      <section className="card p-4">
        <h2 className="mb-3 font-semibold">기본 정보</h2>
        <dl className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-2 text-sm">
          <dt className="text-ink-faint">회원번호</dt>
          <dd className="tabular-nums">#{member.id}</dd>
          <dt className="text-ink-faint">이메일</dt>
          <dd className="break-all">{member.email ?? "-"}</dd>
          <dt className="text-ink-faint">연락처</dt>
          <dd className="tabular-nums">{member.phone ?? "-"}</dd>
          <dt className="text-ink-faint">가입 방법</dt>
          <dd>{member.joinMethods.map((m) => JOIN_METHOD_LABEL[m] ?? m).join(", ") || "-"}</dd>
          <dt className="text-ink-faint">가입일</dt>
          <dd className="tabular-nums">{formatDate(member.createdAt)}</dd>
          <dt className="text-ink-faint">최근 로그인</dt>
          <dd className="tabular-nums">{member.lastLoginAt ? formatDate(member.lastLoginAt) : "-"}</dd>
          {member.withdrawnAt && (
            <>
              <dt className="text-ink-faint">탈퇴일</dt>
              <dd className="tabular-nums">{formatDate(member.withdrawnAt)}</dd>
            </>
          )}
          <dt className="text-ink-faint">마케팅 수신</dt>
          <dd>
            {member.marketingAgreed
              ? `동의 (${member.marketingAgreedAt ? formatDate(member.marketingAgreedAt) : ""})`
              : "동의 안 함"}
          </dd>
        </dl>
      </section>

      <section className="card p-4">
        <h2 className="mb-3 font-semibold">주문</h2>
        <div className="mb-3 grid grid-cols-3 gap-2 text-center">
          <Stat label="결제 주문" value={`${member.orders.count}건`} />
          <Stat label="결제 금액" value={formatWon(member.orders.totalAmount)} />
          <Stat
            label="최근 주문"
            value={
              member.orders.lastOrderedAt
                ? new Date(member.orders.lastOrderedAt).toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" })
                : "-"
            }
          />
        </div>
        {member.recentOrders.length === 0 ? (
          <p className="text-sm text-ink-faint">주문 내역이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-line text-sm">
            {member.recentOrders.map((o) => (
              <li key={o.orderNo} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2">
                <Link href={`/orders/${o.orderNo}`} className="font-mono text-xs hover:underline">
                  {o.orderNo}
                </Link>
                <span className="text-xs text-ink-faint tabular-nums">{formatDate(o.orderedAt)}</span>
                <span className="text-xs">{ORDER_STATUS_LABEL[o.status] ?? o.status}</span>
                <span className="tabular-nums">{formatWon(o.payAmount)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card p-4">
        <h2 className="mb-3 font-semibold">사업자 인증</h2>
        {member.business ? (
          <dl className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-2 text-sm">
            <dt className="text-ink-faint">상태</dt>
            <dd>{BUSINESS_STATUS_LABEL[member.business.status] ?? member.business.status}</dd>
            <dt className="text-ink-faint">상호</dt>
            <dd>{member.business.businessName}</dd>
            <dt className="text-ink-faint">사업자번호</dt>
            <dd className="font-mono">{member.business.businessNo?.replace(/(\d{3})(\d{2})(\d{5})/, "$1-$2-$3")}</dd>
            <dt className="text-ink-faint">대표자</dt>
            <dd>{member.business.ceoName}</dd>
            {member.business.rejectReason && (
              <>
                <dt className="text-ink-faint">거절 사유</dt>
                <dd className="text-danger">{member.business.rejectReason}</dd>
              </>
            )}
          </dl>
        ) : (
          <p className="text-sm text-ink-faint">신청하지 않았습니다.</p>
        )}
      </section>

      <section className="card p-4">
        <h2 className="mb-3 font-semibold">약관·마케팅 동의 이력</h2>
        {member.agreements.length === 0 ? (
          <p className="text-sm text-ink-faint">기록이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-line text-sm">
            {member.agreements.map((a, i) => (
              <li key={i} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2">
                <span>{AGREEMENT_LABEL[a.type] ?? a.type}</span>
                <span className={a.agreed ? "text-ok" : "text-ink-faint"}>{a.agreed ? "동의" : "동의 안 함·철회"}</span>
                <span className="text-xs text-ink-faint tabular-nums">
                  {formatDate(a.createdAt)} · 약관 {a.version}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {member.status !== "WITHDRAWN" && <StatusSection member={member} onChange={setMember} />}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded bg-surface-muted px-2 py-2">
      <div className="text-xs text-ink-faint">{label}</div>
      <div className="font-semibold tabular-nums">{value}</div>
    </div>
  );
}

function StatusSection({ member, onChange }: { member: MemberDetail; onChange: (m: MemberDetail) => void }) {
  const suspended = member.status === "SUSPENDED";
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onChange(await memberApi.changeStatus(member.id, suspended ? "ACTIVE" : "SUSPENDED", reason.trim()));
      setReason("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "처리하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card flex flex-col gap-3 p-4">
      <div>
        <h2 className="font-semibold">이용 정지</h2>
        <p className="text-xs text-ink-soft">
          정지하면 바로 로그아웃되고 다시 로그인할 수 없습니다. 이미 받은 주문은 그대로 처리됩니다.
        </p>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row">
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          maxLength={200}
          placeholder={suspended ? "정지 해제 사유" : "정지 사유 (예: 반복적인 허위 반품)"}
          className="input flex-1"
        />
        <button
          type="submit"
          disabled={busy || !reason.trim()}
          className={suspended ? "btn-primary" : "btn-outline text-danger"}
        >
          {busy ? "처리 중…" : suspended ? "정지 해제" : "이용 정지"}
        </button>
      </form>
      {error && <p className="text-xs text-danger">{error}</p>}
      {member.statusLogs.length > 0 && (
        <ul className="divide-y divide-line border-t border-line text-sm">
          {member.statusLogs.map((log, i) => (
            <li key={i} className="flex flex-wrap gap-x-3 gap-y-1 py-2">
              <span className={log.action === "SUSPEND_USER" ? "text-danger" : "text-ok"}>
                {log.action === "SUSPEND_USER" ? "정지" : "해제"}
              </span>
              <span className="flex-1">{log.reason}</span>
              <span className="text-xs text-ink-faint tabular-nums">
                {log.actorName ?? "-"} · {formatDate(log.createdAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
