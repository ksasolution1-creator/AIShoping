import type { Metadata } from "next";
import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { MemberDetailView } from "./MemberDetailView";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "회원 상세" };

type Params = Promise<{ id: string }>;

export default function MemberDetailPage({ params }: { params: Params }) {
  return (
    <Suspense fallback={<WhaleLoader />}>
      <Loader params={params} />
    </Suspense>
  );
}

async function Loader({ params }: { params: Params }) {
  const { id } = await params;
  return <MemberDetailView key={id} id={Number(id)} />;
}
