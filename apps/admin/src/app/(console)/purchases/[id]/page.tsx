import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { PurchaseView } from "../PurchaseView";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "매입서" };

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default function PurchasePage({ params, searchParams }: Props) {
  return (
    <Suspense fallback={<WhaleLoader />}>
      <PurchaseLoader params={params} searchParams={searchParams} />
    </Suspense>
  );
}

// 주소 값은 요청 시점에만 알 수 있으므로 Suspense 안에서 읽는다
async function PurchaseLoader({ params, searchParams }: Props) {
  const id = Number((await params).id);
  if (!Number.isInteger(id) || id <= 0) notFound();
  const justReceived = (await searchParams).received === "1";
  return <PurchaseView id={id} justReceived={justReceived} />;
}
