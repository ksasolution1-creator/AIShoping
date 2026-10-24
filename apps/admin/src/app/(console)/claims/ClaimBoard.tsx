"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";

import {
  ApiError,
  CLAIM_STATUS_LABEL,
  claimAdminApi,
  formatDate,
  formatWon,
  GRADES,
  gradeLabel,
  orderAdminApi,
  type Carrier,
  type Claim,
  type Page,
} from "@/lib/api";

const TABS = [
  { value: "REQUESTED", label: "신규 반품" },
  { value: "COLLECTING", label: "회수·검수 대기" },
  { value: "COMPLETED", label: "완료" },
  { value: "REJECTED,WITHDRAWN", label: "거절·철회" },
  { value: "", label: "전체" },
];

const STATUS_STYLE: Record<string, string> = {
  REQUESTED: "bg-info/10 text-info",
  COLLECTING: "bg-warn/10 text-warn",
  COMPLETED: "bg-ok/10 text-ok",
  REJECTED: "bg-danger/10 text-danger",
  WITHDRAWN: "bg-surface-muted text-ink-faint",
};

export function ClaimBoard() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "REQUESTED";
  const [data, setData] = useState<Page<Claim> | null>(null);
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  const load = useCallback(
    () =>
      claimAdminApi
        .list({ status })
        .then(setData)
        .catch((e: Error) => setNotice({ kind: "error", text: e.message })),
    [status],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    orderAdminApi.carriers().then(setCarriers).catch(() => setCarriers([]));
  }, []);

  async function act(action: () => Promise<Claim>, done: string) {
    try {
      await action();
      setNotice({ kind: "ok", text: done });
      await load();
      return true;
    } catch (e) {
      setNotice({ kind: "error", text: e instanceof ApiError ? e.message : "처리하지 못했습니다." });
      return false;
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <nav className="flex gap-1 overflow-x-auto" aria-label="처리 상태">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => router.push(`/claims?status=${tab.value}`)}
            aria-current={status === tab.value ? "page" : undefined}
            className={`rounded px-3 py-1.5 text-sm whitespace-nowrap ${
              status === tab.value ? "bg-ink font-semibold text-white" : "bg-surface text-ink-soft hover:bg-surface-muted"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {notice && (
        <p
          role="status"
          className={`rounded p-3 text-sm ${notice.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
        >
          {notice.text}
        </p>
      )}

      <ul className="flex flex-col gap-3">
        {data?.items.map((claim) => (
          <ClaimCard key={claim.id} claim={claim} carriers={carriers} act={act} />
        ))}
        {data && data.items.length === 0 && (
          <li className="card px-4 py-12 text-center text-sm text-ink-faint">처리할 건이 없습니다.</li>
        )}
        {!data && (
          <li>
            <WhaleLoader />
          </li>
        )}
      </ul>
    </div>
  );
}

function ClaimCard({
  claim,
  carriers,
  act,
}: {
  claim: Claim;
  carriers: Carrier[];
  act: (action: () => Promise<Claim>, done: string) => Promise<boolean>;
}) {
  const [panel, setPanel] = useState<"none" | "approve" | "reject" | "complete">("none");
  const isReturn = claim.type === "RETURN";
  const open = claim.status === "REQUESTED" || claim.status === "COLLECTING" || claim.status === "INSPECTING";
  const expectedRefund = Math.max(claim.lineTotal - claim.returnShippingFee, 0);

  return (
    <li className="card flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex flex-col gap-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[claim.status] ?? "bg-surface-muted"}`}>
              {isReturn ? "반품" : "취소"} · {CLAIM_STATUS_LABEL[claim.status]}
            </span>
            <span className="font-medium">{claim.productName}</span>
            <span className="text-xs text-ink-faint">
              {[gradeLabel(claim.conditionGrade), claim.optionText].filter(Boolean).join(" · ")} · {claim.qty}개
            </span>
          </div>
          <p className="text-xs text-ink-faint">
            <Link href={`/orders/${claim.orderNo}`} className="font-mono underline underline-offset-2">
              {claim.orderNo}
            </Link>{" "}
            · {claim.ordererName} · 신청 {formatDate(claim.requestedAt)}
          </p>
        </div>
        <span className="text-sm font-semibold tabular-nums">{formatWon(claim.lineTotal)}</span>
      </div>

      <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
        <div className="flex gap-2">
          <dt className="text-ink-faint">사유</dt>
          <dd>
            {claim.reasonLabel}
            {claim.fault && (
              <span className={`ml-1.5 text-xs ${claim.fault === "CUSTOMER" ? "text-warn" : "text-info"}`}>
                ({claim.fault === "CUSTOMER" ? "고객 귀책" : "판매자 귀책"})
              </span>
            )}
          </dd>
        </div>
        {isReturn && (
          <div className="flex gap-2">
            <dt className="text-ink-faint">반품 배송비</dt>
            <dd className="tabular-nums">{claim.returnShippingFee > 0 ? `${formatWon(claim.returnShippingFee)} (고객 부담)` : "없음"}</dd>
          </div>
        )}
        {claim.reasonDetail && (
          <div className="flex gap-2 sm:col-span-2">
            <dt className="shrink-0 text-ink-faint">내용</dt>
            <dd className="whitespace-pre-line">{claim.reasonDetail}</dd>
          </div>
        )}
        {claim.pickupTrackingNo && (
          <div className="flex gap-2">
            <dt className="text-ink-faint">회수 송장</dt>
            <dd>
              {claim.pickupCarrierName} <span className="font-mono">{claim.pickupTrackingNo}</span>
            </dd>
          </div>
        )}
        {claim.inspectionGrade && (
          <div className="flex gap-2">
            <dt className="text-ink-faint">검수 결과</dt>
            <dd>{claim.inspectionGrade === "DISPOSE" ? "폐기" : GRADES.find((g) => g.code === claim.inspectionGrade)?.label}</dd>
          </div>
        )}
        {claim.refundAmount !== null && (
          <div className="flex gap-2">
            <dt className="text-ink-faint">환불</dt>
            <dd className="font-semibold tabular-nums">{formatWon(claim.refundAmount)}</dd>
          </div>
        )}
        {claim.rejectReason && (
          <div className="flex gap-2 sm:col-span-2">
            <dt className="text-ink-faint">거절 사유</dt>
            <dd className="text-danger">{claim.rejectReason}</dd>
          </div>
        )}
      </dl>

      {isReturn && open && panel === "none" && (
        <div className="flex flex-wrap gap-2">
          {claim.status === "REQUESTED" && (
            <button type="button" className="btn-outline" onClick={() => setPanel("approve")}>
              승인·회수 접수
            </button>
          )}
          <button type="button" className="btn-primary" onClick={() => setPanel("complete")}>
            입고·검수 완료
          </button>
          <button type="button" className="btn-outline text-danger" onClick={() => setPanel("reject")}>
            거절
          </button>
        </div>
      )}

      {panel === "approve" && (
        <form
          className="flex flex-wrap items-end gap-2 rounded bg-canvas p-3"
          onSubmit={async (e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            const carrier = String(form.get("carrier") ?? "");
            const ok = await act(
              () => claimAdminApi.approve(claim.id, carrier ? Number(carrier) : null, String(form.get("tracking") ?? "").trim()),
              "반품을 승인했습니다. 상품이 도착하면 검수를 완료해 주세요.",
            );
            if (ok) setPanel("none");
          }}
        >
          <label className="flex flex-col gap-1">
            <span className="field-label">회수 택배사 (선택)</span>
            <select id={`carrier-${claim.id}`} name="carrier" className="input w-36" defaultValue="">
              <option value="">나중에 입력</option>
              {carriers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            <span className="field-label">회수 송장번호 (선택)</span>
            <input id={`tracking-${claim.id}`} name="tracking" className="input w-44 font-mono" inputMode="numeric" />
          </label>
          <button type="button" className="btn-outline" onClick={() => setPanel("none")}>
            닫기
          </button>
          <button type="submit" className="btn-primary">
            승인
          </button>
        </form>
      )}

      {panel === "complete" && (
        <form
          className="flex flex-col gap-3 rounded bg-canvas p-3"
          onSubmit={async (e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            const ok = await act(
              () => claimAdminApi.complete(claim.id, String(form.get("grade")), String(form.get("note") ?? "")),
              "검수를 마치고 환불했습니다.",
            );
            if (ok) setPanel("none");
          }}
        >
          <fieldset className="flex flex-col gap-2">
            <legend className="field-label mb-1">검수 결과 (재입고할 상태등급)</legend>
            <div className="flex flex-wrap gap-2">
              {[...GRADES, { code: "DISPOSE", label: "폐기 (재입고 안 함)" }].map((g) => (
                <label
                  key={g.code}
                  className="flex cursor-pointer items-center gap-1.5 rounded border border-line bg-surface px-2.5 py-1.5 text-sm has-checked:border-brand has-checked:bg-brand/5"
                >
                  <input type="radio" name="grade" value={g.code} required className="accent-brand" />
                  {g.label}
                </label>
              ))}
            </div>
            <p className="text-xs text-ink-faint">
              같은 상품·옵션에 그 등급 SKU가 없으면 &lsquo;판매중지&rsquo; SKU를 새로 만들어 넣습니다. 가격을 확인한 뒤 판매로 바꿔 주세요.
            </p>
          </fieldset>
          <label className="flex flex-col gap-1">
            <span className="field-label">검수 메모</span>
            <input id={`note-${claim.id}`} name="note" className="input" placeholder="예: 박스 개봉, 구성품 모두 있음" />
          </label>
          <p className="text-sm">
            예상 환불 <strong className="tabular-nums">{formatWon(expectedRefund)}</strong>
            {claim.returnShippingFee > 0 && (
              <span className="text-xs text-ink-faint"> (상품 {formatWon(claim.lineTotal)} − 반품 배송비 {formatWon(claim.returnShippingFee)})</span>
            )}
          </p>
          <div className="flex gap-2">
            <button type="button" className="btn-outline" onClick={() => setPanel("none")}>
              닫기
            </button>
            <button type="submit" className="btn-primary">
              검수 완료·환불
            </button>
          </div>
        </form>
      )}

      {panel === "reject" && (
        <form
          className="flex flex-wrap items-end gap-2 rounded bg-canvas p-3"
          onSubmit={async (e) => {
            e.preventDefault();
            const reason = String(new FormData(e.currentTarget).get("reason") ?? "").trim();
            const ok = await act(() => claimAdminApi.reject(claim.id, reason), "반품을 거절했습니다.");
            if (ok) setPanel("none");
          }}
        >
          <label className="flex min-w-60 flex-1 flex-col gap-1">
            <span className="field-label">거절 사유 (고객에게 보임)</span>
            <input id={`reject-${claim.id}`} name="reason" className="input" required minLength={2} placeholder="예: 사용 흔적이 있어 반품할 수 없습니다" />
          </label>
          <button type="button" className="btn-outline" onClick={() => setPanel("none")}>
            닫기
          </button>
          <button type="submit" className="btn bg-danger text-white">
            거절
          </button>
        </form>
      )}
    </li>
  );
}
