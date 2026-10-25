"use client";

import { useEffect, useState } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { ConfirmButton } from "@/components/ConfirmButton";
import { ApiError, inventoryApi, type Supplier, type SupplierForm } from "@/lib/api";

export function SupplierManager() {
  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  async function remove(s: Supplier) {
    try {
      await inventoryApi.deleteSupplier(s.id);
      setNotice({ kind: "ok", text: `'${s.name}' 매입처를 삭제했습니다.` });
      load();
    } catch (e) {
      setNotice({ kind: "error", text: e instanceof ApiError ? e.message : "삭제하지 못했습니다." });
    }
  }

  function load() {
    inventoryApi
      .suppliers(true)
      .then(setSuppliers)
      .catch((e: Error) => setError(e.message));
  }

  useEffect(load, []);

  if (error) return <p className="rounded bg-danger/10 p-3 text-sm text-danger">{error}</p>;
  if (!suppliers) return <WhaleLoader />;

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <div className="flex justify-end">
        <button type="button" className="btn-primary" onClick={() => setEditing("new")}>
          매입처 추가
        </button>
      </div>
      {notice && (
        <p
          role="status"
          className={`rounded p-3 text-sm ${notice.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
        >
          {notice.text}
        </p>
      )}
      {editing === "new" && (
        <SupplierEditor
          initial={null}
          onDone={() => {
            setEditing(null);
            load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}
      <ul className="card divide-y divide-line">
        {suppliers.map((s) =>
          editing === s.id ? (
            <li key={s.id} className="p-3">
              <SupplierEditor
                initial={s}
                onDone={() => {
                  setEditing(null);
                  load();
                }}
                onCancel={() => setEditing(null)}
              />
            </li>
          ) : (
            <li key={s.id} className={`flex items-center gap-4 px-4 py-3 text-sm ${s.active ? "" : "opacity-50"}`}>
              <div className="flex-1">
                <div className="font-medium">
                  {s.name}
                  {!s.active && <span className="ml-2 text-xs text-ink-faint">(거래 중지)</span>}
                </div>
                <div className="text-xs text-ink-soft">
                  {[s.businessNo && `사업자 ${s.businessNo}`, s.contactName, s.phone, s.memo].filter(Boolean).join(" · ") || "-"}
                </div>
              </div>
              <button type="button" className="btn-outline h-8" onClick={() => setEditing(s.id)}>
                수정
              </button>
              <ConfirmButton className="btn-outline h-8 text-danger" onConfirm={() => remove(s)} />
            </li>
          ),
        )}
        {suppliers.length === 0 && editing !== "new" && (
          <li className="px-4 py-10 text-center text-sm text-ink-faint">
            등록된 매입처가 없습니다. 물건을 사 오는 도매상·업체를 등록해 두면 매입 내역을 업체별로 볼 수 있습니다.
          </li>
        )}
      </ul>
    </div>
  );
}

function SupplierEditor({
  initial,
  onDone,
  onCancel,
}: {
  initial: Supplier | null;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const text = (key: string) => String(data.get(key) ?? "").trim() || null;
    const body: SupplierForm = {
      name: String(data.get("name")).trim(),
      businessNo: text("businessNo"),
      contactName: text("contactName"),
      phone: text("phone"),
      memo: text("memo"),
      active: data.get("active") === "on",
    };
    try {
      if (initial) await inventoryApi.updateSupplier(initial.id, body);
      else await inventoryApi.createSupplier(body);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : "저장하지 못했습니다.");
    }
  }

  return (
    <form onSubmit={submit} className="card grid gap-3 p-4 sm:grid-cols-3">
      <Field name="name" label="매입처 이름" defaultValue={initial?.name} required />
      <Field name="businessNo" label="사업자등록번호" defaultValue={initial?.businessNo} />
      <Field name="contactName" label="담당자" defaultValue={initial?.contactName} />
      <Field name="phone" label="연락처" defaultValue={initial?.phone} />
      <Field name="memo" label="메모" defaultValue={initial?.memo} wide />
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" name="active" defaultChecked={initial?.active ?? true} />
        거래 중
      </label>
      {error && <p className="text-sm text-danger sm:col-span-3">{error}</p>}
      <div className="flex justify-end gap-2 sm:col-span-3">
        <button type="button" className="btn-outline" onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn-primary">
          저장
        </button>
      </div>
    </form>
  );
}

function Field({
  name,
  label,
  defaultValue,
  required,
  wide,
}: {
  name: string;
  label: string;
  defaultValue?: string | null;
  required?: boolean;
  wide?: boolean;
}) {
  return (
    <label className={`flex flex-col gap-1 ${wide ? "sm:col-span-2" : ""}`}>
      <span className="field-label">{label}</span>
      <input name={name} defaultValue={defaultValue ?? ""} required={required} className="input" />
    </label>
  );
}
