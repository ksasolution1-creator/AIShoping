import { Suspense } from "react";

import { WhaleLoader } from "@/components/brand/WhaleLoader";
import { OrderDetailView } from "./OrderDetailView";

type Params = Promise<{ orderNo: string }>;

export default function OrderDetailPage({ params }: { params: Params }) {
  return (
    <Suspense fallback={<WhaleLoader label="주문을 불러오는 중이에요" />}>
      <Loader params={params} />
    </Suspense>
  );
}

async function Loader({ params }: { params: Params }) {
  const { orderNo } = await params;
  return <OrderDetailView key={orderNo} orderNo={orderNo} />;
}
