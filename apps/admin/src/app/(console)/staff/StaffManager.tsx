"use client";

import { useEffect, useState } from "react";

import { useMe } from "@/components/AuthGate";
import { WhaleLoader } from "@/components/brand/WhaleLoader";
import {
  ApiError,
  formatDate,
  STAFF_ROLE_LABEL,
  staffApi,
  type Staff,
  type StaffRole,
} from "@/lib/api";

const MIN_PASSWORD = 10;

export function StaffManager() {
  const [staff, setStaff] = useState<Staff[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    staffApi
      .list()
      .then(setStaff)
      .catch((e: Error) => setError(e.message));
  }, []);

  if (error && !staff)
    return (
      <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>
    );
  if (!staff) return <WhaleLoader />;

  return (
    <div className="flex flex-col gap-5">
      <div className="rounded bg-surface-muted p-3 text-xs leading-relaxed text-ink-soft">
        <b>관리자</b>는 모든 메뉴와 운영자 계정 관리까지, <b>운영자</b>는
        상품·주문·회원 등 일상 업무만 할 수 있습니다. 새 계정은 처음 정한
        비밀번호로 로그인한 뒤 메뉴 아래 &lsquo;비밀번호 변경&rsquo;에서 직접
        바꾸도록 안내해 주세요.
      </div>

      <CreateStaffForm onCreated={setStaff} />

      <ul className="flex flex-col gap-2">
        {staff.map((s) => (
          <StaffCard key={s.id} staff={s} onChange={setStaff} />
        ))}
      </ul>
    </div>
  );
}

function CreateStaffForm({
  onCreated,
}: {
  onCreated: (list: Staff[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    name: "",
    email: "",
    password: "",
    role: "OPERATOR" as StaffRole,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (form.password.length < MIN_PASSWORD) {
      setFieldErrors({
        password: `비밀번호는 ${MIN_PASSWORD}자 이상으로 정해 주세요.`,
      });
      return;
    }
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      onCreated(
        await staffApi.create({
          ...form,
          name: form.name.trim(),
          email: form.email.trim(),
        }),
      );
      setForm({ name: "", email: "", password: "", role: "OPERATOR" });
      setOpen(false);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message);
        setFieldErrors(e.fieldErrors);
      } else {
        setError("계정을 만들지 못했습니다.");
      }
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        className="btn-primary self-start"
        onClick={() => setOpen(true)}
      >
        + 운영자 추가
      </button>
    );
  }

  return (
    <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-2">
      <h2 className="font-semibold sm:col-span-2">운영자 추가</h2>
      <label className="flex flex-col gap-1">
        <span className="field-label">이름 *</span>
        <input
          className="input"
          value={form.name}
          onChange={(e) => set("name", e.target.value)}
          required
          maxLength={50}
        />
        {fieldErrors.name && (
          <span className="text-xs text-danger">{fieldErrors.name}</span>
        )}
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">로그인 이메일 *</span>
        <input
          type="email"
          className="input"
          value={form.email}
          onChange={(e) => set("email", e.target.value)}
          required
          autoComplete="off"
        />
        {fieldErrors.email && (
          <span className="text-xs text-danger">{fieldErrors.email}</span>
        )}
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">
          처음 비밀번호 * ({MIN_PASSWORD}자 이상)
        </span>
        <input
          type="text"
          className="input font-mono"
          value={form.password}
          onChange={(e) => set("password", e.target.value)}
          required
          autoComplete="new-password"
        />
        {fieldErrors.password && (
          <span className="text-xs text-danger">{fieldErrors.password}</span>
        )}
      </label>
      <label className="flex flex-col gap-1">
        <span className="field-label">권한 *</span>
        <select
          className="input"
          value={form.role}
          onChange={(e) => set("role", e.target.value as StaffRole)}
        >
          <option value="OPERATOR">운영자 (일상 업무)</option>
          <option value="ADMIN">관리자 (운영자 계정 관리 포함)</option>
        </select>
      </label>
      {error && <p className="text-sm text-danger sm:col-span-2">{error}</p>}
      <div className="flex justify-end gap-2 sm:col-span-2">
        <button
          type="button"
          className="btn-outline"
          onClick={() => setOpen(false)}
        >
          취소
        </button>
        <button type="submit" className="btn-primary" disabled={busy}>
          {busy ? "만드는 중…" : "계정 만들기"}
        </button>
      </div>
    </form>
  );
}

function StaffCard({
  staff,
  onChange,
}: {
  staff: Staff;
  onChange: (list: Staff[]) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [resetting, setResetting] = useState(false);
  const [password, setPassword] = useState("");
  const suspended = staff.status === "SUSPENDED";
  const isMe = useMe()?.id === staff.id;

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "처리하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="card flex flex-col gap-3 p-4 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-semibold">{staff.name}</span>
            <span className="rounded bg-surface-muted px-1.5 py-0.5 text-xs">
              {STAFF_ROLE_LABEL[staff.role]}
            </span>
            {suspended && (
              <span className="rounded bg-danger/10 px-1.5 py-0.5 text-xs text-danger">
                정지
              </span>
            )}
            {isMe && <span className="text-xs text-ink-faint">(내 계정)</span>}
          </div>
          <div className="break-all text-ink-soft">{staff.email}</div>
          <div className="text-xs text-ink-faint tabular-nums">
            추가 {formatDate(staff.createdAt)} · 최근 로그인{" "}
            {staff.lastLoginAt ? formatDate(staff.lastLoginAt) : "-"}
          </div>
        </div>
        {!isMe && (
          <div className="flex flex-wrap gap-2">
            <select
              aria-label="권한"
              className="input w-auto"
              value={staff.role}
              disabled={busy}
              onChange={(e) =>
                run(async () => {
                  onChange(
                    await staffApi.change(staff.id, {
                      role: e.target.value as StaffRole,
                    }),
                  );
                  setNotice(
                    "권한을 바꿨습니다. 해당 계정은 다시 로그인해야 합니다.",
                  );
                })
              }
            >
              <option value="OPERATOR">운영자</option>
              <option value="ADMIN">관리자</option>
            </select>
            <button
              type="button"
              className="btn-outline"
              disabled={busy}
              onClick={() => setResetting((v) => !v)}
            >
              비밀번호 초기화
            </button>
            <button
              type="button"
              className={suspended ? "btn-outline" : "btn-outline text-danger"}
              disabled={busy}
              onClick={() =>
                run(async () =>
                  onChange(
                    await staffApi.change(staff.id, {
                      status: suspended ? "ACTIVE" : "SUSPENDED",
                    }),
                  ),
                )
              }
            >
              {suspended ? "정지 해제" : "정지"}
            </button>
          </div>
        )}
      </div>
      {resetting && (
        <form
          className="flex flex-col gap-2 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault();
            if (password.length < MIN_PASSWORD) {
              setError(`비밀번호는 ${MIN_PASSWORD}자 이상으로 정해 주세요.`);
              return;
            }
            void run(async () => {
              await staffApi.resetPassword(staff.id, password);
              setPassword("");
              setResetting(false);
              setNotice(
                "비밀번호를 바꿨습니다. 새 비밀번호를 직접 전달해 주세요.",
              );
            });
          }}
        >
          <input
            type="text"
            className="input flex-1 font-mono"
            placeholder={`새 비밀번호 (${MIN_PASSWORD}자 이상)`}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
          />
          <button type="submit" className="btn-primary" disabled={busy}>
            바꾸기
          </button>
        </form>
      )}
      {notice && <p className="text-xs text-ok">{notice}</p>}
      {error && <p className="text-xs text-danger">{error}</p>}
    </li>
  );
}
