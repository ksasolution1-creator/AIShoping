import { notFound } from "next/navigation";
import { Suspense } from "react";

import { ProductEditor } from "./ProductEditor";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default function ProductDetailPage({ params, searchParams }: Props) {
  return (
    <Suspense fallback={<p className="text-sm text-ink-faint">불러오는 중…</p>}>
      <EditorLoader params={params} searchParams={searchParams} />
    </Suspense>
  );
}

// 주소 값은 요청 시점에만 알 수 있으므로 Suspense 안에서 읽는다
async function EditorLoader({ params, searchParams }: Props) {
  const id = Number((await params).id);
  if (!Number.isInteger(id) || id <= 0) notFound();
  const created = (await searchParams).created === "1";
  return <ProductEditor key={id} id={id} created={created} />;
}
