import { Suspense } from "react";

import { OrderDetailView } from "./OrderDetailView";

type Params = Promise<{ orderNo: string }>;

export default function OrderDetailPage({ params }: { params: Params }) {
  return (
    <Suspense fallback={<p className="py-10 text-sm text-ink-faint">불러오는 중…</p>}>
      <Loader params={params} />
    </Suspense>
  );
}

async function Loader({ params }: { params: Params }) {
  const { orderNo } = await params;
  return <OrderDetailView key={orderNo} orderNo={orderNo} />;
}
