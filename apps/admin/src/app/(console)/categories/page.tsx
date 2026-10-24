"use client";

import { useEffect, useState } from "react";

import { api, ApiError, type Category } from "@/lib/api";

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [name, setName] = useState("");
  const [parentId, setParentId] = useState("");
  const [message, setMessage] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  const load = () => api.categories().then(setCategories);

  useEffect(() => {
    api
      .categories()
      .then(setCategories)
      .catch(() => setMessage({ kind: "error", text: "카테고리를 불러오지 못했습니다." }));
  }, []);

  async function add(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    try {
      await api.createCategory({ name: name.trim(), parentId: parentId ? Number(parentId) : null });
      setName("");
      await load();
      setMessage({ kind: "ok", text: `'${name.trim()}' 카테고리를 추가했습니다.` });
    } catch (e) {
      setMessage({ kind: "error", text: e instanceof ApiError ? e.message : "저장하지 못했습니다." });
    }
  }

  const roots = categories.filter((c) => c.parentId === null);
  const children = (id: number) => categories.filter((c) => c.parentId === id);

  return (
    <div className="flex max-w-2xl flex-col gap-5">
      <h1 className="text-xl font-bold">카테고리</h1>

      <form onSubmit={add} className="card flex flex-wrap items-end gap-3 p-4">
        <label className="flex min-w-40 flex-1 flex-col gap-1">
          <span className="field-label">이름</span>
          <input id="cat-name" className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="field-label">상위 카테고리</span>
          <select id="cat-parent" className="input w-44" value={parentId} onChange={(e) => setParentId(e.target.value)}>
            <option value="">없음 (최상위)</option>
            {roots.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn-primary" disabled={!name.trim()}>
          추가
        </button>
      </form>

      {message && (
        <p
          role="status"
          className={`rounded p-3 text-sm ${message.kind === "ok" ? "bg-ok/10 text-ok" : "bg-danger/10 text-danger"}`}
        >
          {message.text}
        </p>
      )}

      <ul className="card divide-y divide-line">
        {roots.map((root) => (
          <li key={root.id} className="px-4 py-3">
            <span className="font-medium">{root.name}</span>
            {children(root.id).length > 0 && (
              <ul className="mt-1.5 flex flex-wrap gap-1.5 pl-3">
                {children(root.id).map((child) => (
                  <li key={child.id} className="rounded bg-surface-muted px-2 py-0.5 text-sm text-ink-soft">
                    {child.name}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
        {roots.length === 0 && <li className="px-4 py-8 text-center text-sm text-ink-faint">카테고리가 없습니다.</li>}
      </ul>
    </div>
  );
}
