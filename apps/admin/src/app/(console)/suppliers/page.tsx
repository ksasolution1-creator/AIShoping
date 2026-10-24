import type { Metadata } from "next";

import { InventoryTabs } from "@/components/InventoryTabs";
import { SupplierManager } from "./SupplierManager";

// 관리자 영역은 로그인 확인 뒤에 그리므로 즉시 이동 검증에서 뺀다 ((console)/layout.tsx 참고)
export const instant = false;

export const metadata: Metadata = { title: "매입처" };

export default function SuppliersPage() {
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-xl font-bold">재고·매입</h1>
      <InventoryTabs current="/suppliers" />
      <SupplierManager />
    </div>
  );
}
