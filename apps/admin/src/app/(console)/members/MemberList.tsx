"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  BUSINESS_STATUS_LABEL,
  formatDate,
  JOIN_METHOD_LABEL,
  MEMBER_STATUS_LABEL,
  memberApi,
  type MemberQuery,
  type MemberRow,
  type Page,
} from "@/lib/api";

const FILTER_KEYS = ["q", "method", "status", "from", "to"] as const;

export function MemberList() {
  const router = useRouter();
  const params = useSearchParams();
  const query: MemberQuery = {
    q: params.get("q") ?? "",
    method: params.get("method") ?? "",
    status: params.get("status") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    page: Number(params.get("page") ?? 0) || 0,
  };
  const queryKey = params.toString();

  const [data, setData] = useState<Page<MemberRow> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    memberApi
      .list(Object.fromEntries(new URLSearchParams(queryKey)))
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [queryKey]);

  function go(change: MemberQuery) {
    const merged = { ...query, page: 0, ...change };
    const next = new URLSearchParams();
    for (const key of [...FILTER_KEYS, "page"] as const) {
      const value = merged[key];
      if (value) next.set(key, String(value));
    }
    router.push(`/members${next.size ? `?${next}` : ""}`);
  }

  return (
    <div className="flex flex-col gap-4">
      <form
        key={queryKey}
        className="card grid gap-3 p-3 sm:grid-cols-2 lg:flex lg:flex-wrap lg:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          const form = new FormData(e.currentTarget);
          go(Object.fromEntries(FILTER_KEYS.map((k) => [k, String(form.get(k) ?? "").trim()])));
        }}
      >
        <label className="flex min-w-48 flex-1 flex-col gap-1 sm:col-span-2">
          <span className="field-label">검색 (이름, 이메일)</span>
          <input name="q" defaultValue={query.q} className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">가입 방법</span>
          <select name="method" defaultValue={query.method} className="input lg:w-28">
            <option value="">전체</option>
            <option value="EMAIL">이메일</option>
            <option value="KAKAO">카카오</option>
            <option value="NAVER">네이버</option>
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">상태</span>
          <select name="status" defaultValue={query.status} className="input lg:w-24">
            <option value="">전체</option>
            <option value="ACTIVE">정상</option>
            <option value="SUSPENDED">정지</option>
            <option value="WITHDRAWN">탈퇴</option>
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">가입일 (부터)</span>
          <input type="date" name="from" defaultValue={query.from} className="input" />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">가입일 (까지)</span>
          <input type="date" name="to" defaultValue={query.to} className="input" />
        </label>
        <div className="flex gap-2 sm:col-span-2 lg:col-span-1">
          <button type="submit" className="btn-primary flex-1 lg:flex-none">
            검색
          </button>
          <button type="button" className="btn-outline" onClick={() => router.push("/members")}>
            초기화
          </button>
        </div>
      </form>

      {error && <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>}
      {!data && !error && <WhaleLoader size="sm" label="회원을 불러오는 중이에요" />}
      {data && (
        <p className="text-xs text-ink-soft">
          {data.total.toLocaleString("ko-KR")}명 · 연락처 전체는 회원 상세에서 볼 수 있고, 열람 기록이 남습니다.
        </p>
      )}
      {data && data.items.length === 0 && (
        <p className="card p-10 text-center text-sm text-ink-faint">조건에 맞는 회원이 없습니다.</p>
      )}

      {/* 휴대폰: 표 대신 카드 목록 (글자가 잘리지 않게) */}
      <ul className="flex flex-col gap-2 md:hidden">
        {data?.items.map((m) => (
          <li key={m.id}>
            <Link href={`/members/${m.id}`} className="card flex flex-col gap-1.5 p-3 text-sm">
              <span className="flex items-center justify-between gap-2">
                <span className="font-semibold">{m.name}</span>
                <StatusBadge status={m.status} />
              </span>
              <span className="break-all text-ink-soft">{m.email ?? "-"}</span>
              <span className="flex flex-wrap gap-1">
                <Methods methods={m.joinMethods} />
                {m.businessStatus && <Tag>{BUSINESS_STATUS_LABEL[m.businessStatus] ?? m.businessStatus}</Tag>}
              </span>
              <span className="text-xs text-ink-faint tabular-nums">
                가입 {formatDate(m.createdAt)} · 최근 로그인 {m.lastLoginAt ? formatDate(m.lastLoginAt) : "-"}
              </span>
            </Link>
          </li>
        ))}
      </ul>

      {data && data.items.length > 0 && (
        <div className="card hidden overflow-x-auto md:block">
          <table className="w-full text-sm">
            <thead className="border-b border-line bg-surface-muted text-left text-xs text-ink-soft">
              <tr>
                <th className="px-3 py-2.5 font-medium">이름</th>
                <th className="px-3 py-2.5 font-medium">이메일</th>
                <th className="px-3 py-2.5 font-medium">가입 방법</th>
                <th className="px-3 py-2.5 font-medium">연락처</th>
                <th className="px-3 py-2.5 font-medium">가입일</th>
                <th className="px-3 py-2.5 font-medium">최근 로그인</th>
                <th className="px-3 py-2.5 font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((m) => (
                <tr key={m.id} className="border-b border-line last:border-0 hover:bg-surface-muted/60">
                  <td className="px-3 py-2 whitespace-nowrap">
                    <Link href={`/members/${m.id}`} className="font-medium hover:underline">
                      {m.name}
                    </Link>
                    {m.businessStatus && (
                      <span className="ml-1.5">
                        <Tag>{BUSINESS_STATUS_LABEL[m.businessStatus] ?? m.businessStatus}</Tag>
                      </span>
                    )}
                  </td>
                  <td className="max-w-56 truncate px-3 py-2 text-ink-soft" title={m.email ?? undefined}>
                    {m.email ?? "-"}
                  </td>
                  <td className="px-3 py-2 whitespace-nowrap">
                    <Methods methods={m.joinMethods} />
                  </td>
                  <td className="px-3 py-2 whitespace-nowrap text-ink-soft tabular-nums">{m.phoneMasked ?? "-"}</td>
                  <td className="px-3 py-2 text-xs whitespace-nowrap text-ink-faint tabular-nums">
                    {formatDate(m.createdAt)}
                  </td>
                  <td className="px-3 py-2 text-xs whitespace-nowrap text-ink-faint tabular-nums">
                    {m.lastLoginAt ? formatDate(m.lastLoginAt) : "-"}
                  </td>
                  <td className="px-3 py-2">
                    <StatusBadge status={m.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <nav className="flex items-center justify-center gap-3 text-sm" aria-label="페이지">
          <button type="button" className="btn-outline" disabled={data.page === 0} onClick={() => go({ page: data.page - 1 })}>
            이전
          </button>
          <span className="tabular-nums">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            className="btn-outline"
            disabled={data.page + 1 >= data.totalPages}
            onClick={() => go({ page: data.page + 1 })}
          >
            다음
          </button>
        </nav>
      )}
    </div>
  );
}

function Methods({ methods }: { methods: MemberRow["joinMethods"] }) {
  if (methods.length === 0) return <span className="text-ink-faint">-</span>;
  return (
    <span className="inline-flex flex-wrap gap-1">
      {methods.map((m) => (
        <Tag key={m}>{JOIN_METHOD_LABEL[m] ?? m}</Tag>
      ))}
    </span>
  );
}

function Tag({ children }: { children: React.ReactNode }) {
  return <span className="rounded bg-surface-muted px-1.5 py-0.5 text-xs whitespace-nowrap text-ink-soft">{children}</span>;
}

export function StatusBadge({ status }: { status: MemberRow["status"] }) {
  const tone =
    status === "ACTIVE"
      ? "bg-ok/10 text-ok"
      : status === "SUSPENDED"
        ? "bg-danger/10 text-danger"
        : "bg-surface-muted text-ink-faint";
  return (
    <span className={`rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap ${tone}`}>{MEMBER_STATUS_LABEL[status]}</span>
  );
}
