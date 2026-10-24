import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { AdminOrderDetail } from "./AdminOrderDetail";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

type Params = Promise<{ orderNo: string }>;

export default function OrderDetailPage({ params }: { params: Params }) {
  return (
    <Suspense fallback={<WhaleLoader />}>
      <Loader params={params} />
    </Suspense>
  );
}

async function Loader({ params }: { params: Params }) {
  const { orderNo } = await params;
  return <AdminOrderDetail key={orderNo} orderNo={orderNo} />;
}
