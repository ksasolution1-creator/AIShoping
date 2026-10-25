// 관리자 화면은 브라우저에서 /api 로 호출하고, next.config.ts의 rewrites가 API 서버로 넘긴다.

export type GradeCode =
  | "NEW"
  | "RETURN_UNOPENED"
  | "RETURN_OPENED"
  | "REFURB"
  | "USED_A"
  | "USED_B"
  | "USED_C"
  | "DISPLAY";

export const GRADES: { code: GradeCode; label: string }[] = [
  { code: "NEW", label: "새상품" },
  { code: "RETURN_UNOPENED", label: "반품 미개봉" },
  { code: "RETURN_OPENED", label: "반품 개봉" },
  { code: "REFURB", label: "리퍼" },
  { code: "USED_A", label: "중고 A급" },
  { code: "USED_B", label: "중고 B급" },
  { code: "USED_C", label: "중고 C급" },
  { code: "DISPLAY", label: "전시상품" },
];

export type ProductStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "HIDDEN" | "DELETED";
export type SkuStatus = "ACTIVE" | "INACTIVE" | "DELETED";

export const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: "준비중",
  ON_SALE: "판매중",
  SOLD_OUT: "품절",
  HIDDEN: "숨김",
  DELETED: "삭제",
};

export const SKU_STATUS_LABEL: Record<SkuStatus, string> = {
  ACTIVE: "판매",
  INACTIVE: "중지",
  DELETED: "삭제",
};

export type Grade = { code: GradeCode; label: string };
export type Category = { id: number; parentId: number | null; name: string; sortOrder: number };
export type Page<T> = { items: T[]; page: number; size: number; total: number; totalPages: number };

export type AdminProductRow = {
  id: number;
  name: string;
  brand: string | null;
  categoryName: string | null;
  status: ProductStatus;
  thumbnailUrl: string | null;
  skuCount: number;
  minPrice: number | null;
  onHand: number;
  reserved: number;
  createdAt: string;
  /** SKU별 상태등급 (목록에서 바로 변경) */
  skus: SkuGrade[];
};

export type SkuGrade = { skuId: number; optionText: string | null; grade: Grade };

export type AdminSku = {
  id: number;
  skuCode: string;
  optionValues: Record<string, string>;
  grade: Grade;
  conditionNote: string | null;
  barcode: string | null;
  price: number;
  listPrice: number | null;
  businessPrice: number | null;
  status: SkuStatus;
  onHand: number;
  reserved: number;
};

export type ProductImage = { id: number; skuId: number | null; url: string; sortOrder: number };

export type AdminProductDetail = {
  id: number;
  sellerId: number;
  name: string;
  brand: string | null;
  modelName: string | null;
  categoryId: number | null;
  description: string | null;
  noticeInfo: Record<string, string>;
  kcCertNo: string | null;
  shippingFee: number;
  freeShippingThreshold: number | null;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
  businessOnly: boolean;
  imageReuseAllowed: boolean;
  wholesale: WholesaleTerms;
  spec: ProductSpec;
  skus: AdminSku[];
  images: ProductImage[];
};

export type PriceTier = { minQty: number; discountBp: number };
export type ProductSpec = {
  origin: string | null;
  manufacturer: string | null;
  packageSize: string | null;
  packageWeightG: number | null;
};
export type WholesaleTerms = { minOrderQty: number; shipWithinDays: number; tiers: PriceTier[] };

export type ProductFields = {
  name: string;
  brand: string | null;
  modelName: string | null;
  categoryId: number | null;
  description: string | null;
  noticeInfo: Record<string, string>;
  kcCertNo: string | null;
  shippingFee: number;
  freeShippingThreshold: number | null;
};

export type CreateSku = {
  skuCode: string | null;
  optionValues: Record<string, string>;
  conditionGrade: GradeCode;
  conditionNote: string | null;
  barcode: string | null;
  price: number;
  listPrice: number | null;
  initialStock: number;
};

/** API가 돌려준 오류. fieldErrors는 입력칸별 메시지 */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

function readCookie(name: string) {
  const found = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return found ? decodeURIComponent(found.slice(name.length + 1)) : null;
}

/** 변경 요청에 붙일 CSRF 토큰. 쿠키가 없으면 먼저 발급받는다 */
async function csrfToken(refresh: boolean) {
  let token = refresh ? null : readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/auth/csrf");
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

async function request<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (method !== "GET") {
    const token = await csrfToken(retried);
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let res: Response;
  try {
    res = await fetch(path, { ...init, method, headers });
  } catch {
    throw new ApiError(0, "서버에 연결하지 못했습니다. API 서버가 실행 중인지 확인해 주세요.");
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    if (res.status === 403 && body?.code === "CSRF" && !retried) {
      return request<T>(path, init, true);
    }
    // 세션이 끝났으면 로그인 화면으로 보낸다 (로그인 확인 요청 자체는 제외)
    if (res.status === 401 && !path.startsWith("/api/auth/")) {
      // React 밖(공통 요청 함수)이라 라우터를 쓸 수 없고, 화면 상태도 비워야 해서 새로 불러온다
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`);
    }
    throw new ApiError(
      res.status,
      body?.detail ?? `요청을 처리하지 못했습니다. (${res.status})`,
      body?.code,
      body?.fieldErrors ?? {},
    );
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export type Me = {
  id: number;
  email: string | null;
  name: string;
  type: "CUSTOMER" | "STAFF";
  roles: string[];
  sellerId: number | null;
};

export const ADMIN_ROLES = ["ADMIN", "OPERATOR"];

export const authApi = {
  me: async (): Promise<Me | null> => {
    try {
      return await request<Me>("/api/auth/me");
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return null;
      throw e;
    }
  },
  login: (email: string, password: string) =>
    request<Me>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>("/api/me/password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) }),
};

export type OrderSummary = {
  orderNo: string;
  orderedAt: string;
  status: string;
  orderName: string;
  thumbnailUrl: string | null;
  itemCount: number;
  payAmount: number;
  itemStatuses: string[];
};

export type OrderDetail = {
  orderNo: string;
  channel: string;
  orderedAt: string;
  paidAt: string | null;
  status: string;
  ordererName: string;
  ordererEmail: string | null;
  receiver: {
    name: string;
    phone: string;
    zipcode: string;
    address1: string;
    address2: string | null;
    deliveryMemo: string | null;
  };
  items: {
    id: number;
    skuId: number | null;
    productName: string;
    optionText: string | null;
    conditionGrade: string | null;
    thumbnailUrl: string | null;
    qty: number;
    unitPrice: number;
    lineTotal: number;
    status: string;
  }[];
  itemsTotal: number;
  discountTotal: number;
  shippingFee: number;
  payAmount: number;
  payMethod: "TOSS" | "BANK_TRANSFER";
  /** 무통장입금 주문일 때만 */
  bankDeposit: { bankName: string; accountNo: string; accountHolder: string; depositorName: string | null; dueAt: string | null } | null;
  /** 고객이 입력한 무통장입금 환불 계좌 */
  refundAccount: { bankName: string; accountNo: string; holder: string } | null;
  /** REQUESTED: 고객 계좌로 직접 송금해야 하는 환불, DONE: 환불 완료 */
  refunds: { id: number; amount: number; reason: string; status: "REQUESTED" | "DONE"; createdAt: string; completedAt: string | null }[];
  payment: { method: string | null; easyPayProvider: string | null; amount: number; balanceAmount: number; status: string; approvedAt: string | null } | null;
  shipments: { id: number; carrierName: string; trackingNo: string; status: string; shippedAt: string | null }[];
  cancellable: boolean;
};

export type Carrier = { id: number; code: string; name: string };

export const ITEM_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: "입금대기",
  PAID: "결제완료",
  PREPARING: "상품준비중",
  SHIPPED: "배송중",
  DELIVERED: "배송완료",
  CONFIRMED: "구매확정",
  CANCELLED: "취소완료",
  RETURN_REQUESTED: "반품요청",
  RETURNED: "반품완료",
};

export const orderAdminApi = {
  list: (params: { status?: string; q?: string; page?: number }) => {
    const search = new URLSearchParams();
    if (params.status) search.set("status", params.status);
    if (params.q) search.set("q", params.q);
    if (params.page) search.set("page", String(params.page));
    return request<Page<OrderSummary>>(`/api/admin/orders?${search}`);
  },
  detail: (orderNo: string) => request<OrderDetail>(`/api/admin/orders/${orderNo}`),
  confirmDeposit: (orderNo: string) =>
    request<OrderDetail>(`/api/admin/orders/${orderNo}/confirm-deposit`, { method: "POST" }),
  completeRefund: (orderNo: string, refundId: number) =>
    request<OrderDetail>(`/api/admin/orders/${orderNo}/refunds/${refundId}/complete`, { method: "POST" }),
  accept: (orderNo: string) => request<OrderDetail>(`/api/admin/orders/${orderNo}/accept`, { method: "POST" }),
  ship: (orderNo: string, carrierId: number, trackingNo: string) =>
    request<OrderDetail>(`/api/admin/orders/${orderNo}/ship`, {
      method: "POST",
      body: JSON.stringify({ carrierId, trackingNo }),
    }),
  cancel: (orderNo: string, reason: string) =>
    request<OrderDetail>(`/api/admin/orders/${orderNo}/cancel`, { method: "POST", body: JSON.stringify({ reason }) }),
  carriers: () => request<Carrier[]>("/api/admin/carriers"),
};

export type Claim = {
  id: number;
  type: "CANCEL" | "RETURN" | "EXCHANGE";
  status: "REQUESTED" | "APPROVED" | "COLLECTING" | "INSPECTING" | "COMPLETED" | "REJECTED" | "WITHDRAWN";
  orderNo: string;
  orderItemId: number;
  ordererName: string;
  productName: string;
  optionText: string | null;
  conditionGrade: string | null;
  qty: number;
  lineTotal: number;
  reasonCode: string;
  reasonLabel: string;
  reasonDetail: string | null;
  fault: "CUSTOMER" | "SELLER" | null;
  returnShippingFee: number;
  refundAmount: number | null;
  inspectionGrade: string | null;
  rejectReason: string | null;
  pickupCarrierName: string | null;
  pickupTrackingNo: string | null;
  requestedAt: string;
  completedAt: string | null;
};

export const CLAIM_STATUS_LABEL: Record<Claim["status"], string> = {
  REQUESTED: "신청 접수",
  APPROVED: "승인",
  COLLECTING: "회수 중",
  INSPECTING: "검수 중",
  COMPLETED: "완료",
  REJECTED: "거절",
  WITHDRAWN: "고객 철회",
};

export const claimAdminApi = {
  list: (params: { status?: string; type?: string; page?: number }) => {
    const search = new URLSearchParams();
    if (params.status) search.set("status", params.status);
    if (params.type) search.set("type", params.type);
    if (params.page) search.set("page", String(params.page));
    return request<Page<Claim>>(`/api/admin/claims?${search}`);
  },
  approve: (id: number, pickupCarrierId: number | null, pickupTrackingNo: string) =>
    request<Claim>(`/api/admin/claims/${id}/approve`, {
      method: "POST",
      body: JSON.stringify({ pickupCarrierId, pickupTrackingNo }),
    }),
  reject: (id: number, reason: string) =>
    request<Claim>(`/api/admin/claims/${id}/reject`, { method: "POST", body: JSON.stringify({ reason }) }),
  complete: (id: number, inspectionGrade: string, note: string) =>
    request<Claim>(`/api/admin/claims/${id}/complete`, {
      method: "POST",
      body: JSON.stringify({ inspectionGrade, note }),
    }),
  cancelItem: (orderNo: string, itemId: number, reason: string) =>
    request<Claim>(`/api/admin/orders/${orderNo}/items/${itemId}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),
};

export const api = {
  categories: () => request<Category[]>("/api/categories"),
  createCategory: (body: { name: string; parentId: number | null }) =>
    request<{ id: number }>("/api/admin/categories", { method: "POST", body: JSON.stringify(body) }),

  products: (params: { q?: string; status?: string; page?: number }) => {
    const search = new URLSearchParams();
    if (params.q) search.set("q", params.q);
    if (params.status) search.set("status", params.status);
    if (params.page) search.set("page", String(params.page));
    return request<Page<AdminProductRow>>(`/api/admin/products?${search}`);
  },
  product: (id: number) => request<AdminProductDetail>(`/api/admin/products/${id}`),
  createProduct: (body: { product: ProductFields; skus: CreateSku[] }) =>
    request<{ id: number }>("/api/admin/products", { method: "POST", body: JSON.stringify(body) }),
  updateProduct: (id: number, body: ProductFields) =>
    request<AdminProductDetail>(`/api/admin/products/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  updateSpec: (id: number, body: ProductSpec) =>
    request<AdminProductDetail>(`/api/admin/products/${id}/spec`, { method: "PUT", body: JSON.stringify(body) }),
  updateWholesale: (id: number, body: WholesaleTerms) =>
    request<AdminProductDetail>(`/api/admin/products/${id}/wholesale`, { method: "PUT", body: JSON.stringify(body) }),
  changeStatus: (id: number, status: ProductStatus) =>
    request<AdminProductDetail>(`/api/admin/products/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  addSku: (id: number, body: CreateSku) =>
    request<AdminProductDetail>(`/api/admin/products/${id}/skus`, { method: "POST", body: JSON.stringify(body) }),
  updateSku: (
    skuId: number,
    body: {
      conditionGrade: GradeCode;
      optionValues: Record<string, string>;
      conditionNote: string | null;
      barcode: string | null;
      price: number;
      listPrice: number | null;
      status: SkuStatus;
    },
  ) => request<void>(`/api/admin/skus/${skuId}`, { method: "PUT", body: JSON.stringify(body) }),
  changeSkuGrade: (skuId: number, conditionGrade: GradeCode) =>
    request<void>(`/api/admin/skus/${skuId}/grade`, { method: "PATCH", body: JSON.stringify({ conditionGrade }) }),
  adjustStock: (skuId: number, body: { qty: number; memo: string | null }) =>
    request<void>(`/api/admin/skus/${skuId}/stock-adjustments`, { method: "POST", body: JSON.stringify(body) }),
  uploadImage: (id: number, file: File, skuId: number | null) => {
    const form = new FormData();
    form.append("file", file);
    const query = skuId ? `?skuId=${skuId}` : "";
    return request<ProductImage>(`/api/admin/products/${id}/images${query}`, { method: "POST", body: form });
  },
  deleteImage: (id: number, imageId: number) =>
    request<void>(`/api/admin/products/${id}/images/${imageId}`, { method: "DELETE" }),
};

const won = new Intl.NumberFormat("ko-KR");
export const formatWon = (n: number | null | undefined) => (n == null ? "-" : `${won.format(n)}원`);
export const formatDate = (iso: string) =>
  new Date(iso).toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });

/** "색상=블랙, 사이즈=M" ↔ {색상: 블랙, 사이즈: M} */
export function parseOptions(text: string): Record<string, string> {
  return Object.fromEntries(
    text
      .split(",")
      .map((part) => part.split("=").map((s) => s.trim()))
      .filter(([key, value]) => key && value),
  );
}

export function optionsToText(options: Record<string, string>) {
  return Object.entries(options)
    .map(([key, value]) => `${key}=${value}`)
    .join(", ");
}

/** 상태등급 코드 → 이름 (예: USED_B → 중고 B급) */
export function gradeLabel(code: string | null | undefined) {
  if (!code) return null;
  return GRADES.find((g) => g.code === code)?.label ?? code;
}

// ------------------------------------------------------------------
// 재고·매입
// ------------------------------------------------------------------

export type Location = { id: number; name: string; type: "WAREHOUSE" | "STORE" };
export type LocationStock = { locationId: number; locationName: string; onHand: number; reserved: number };

export type StockRow = {
  skuId: number;
  skuCode: string;
  barcode: string | null;
  productId: number;
  productName: string;
  optionText: string;
  grade: Grade;
  price: number;
  cost: number | null;
  onHand: number;
  reserved: number;
  available: number;
  locations: LocationStock[];
};

export type Movement = {
  id: number;
  createdAt: string;
  locationName: string;
  qty: number;
  type: string;
  refType: string | null;
  refId: number | null;
  memo: string | null;
  actorName: string | null;
};

export const MOVEMENT_LABEL: Record<string, string> = {
  PURCHASE_IN: "매입 입고",
  SALE_OUT: "판매 출고",
  RETURN_IN: "반품 입고",
  ADJUST: "조정",
  TRANSFER_IN: "이동 입고",
  TRANSFER_OUT: "이동 출고",
  GRADE_CHANGE: "등급 변경",
};

export type Supplier = {
  id: number;
  name: string;
  businessNo: string | null;
  contactName: string | null;
  phone: string | null;
  memo: string | null;
  active: boolean;
};
export type SupplierForm = Omit<Supplier, "id" | "active"> & { active?: boolean };

export type PurchaseStatus = "DRAFT" | "RECEIVED" | "CANCELLED";
export const PURCHASE_STATUS_LABEL: Record<PurchaseStatus, string> = {
  DRAFT: "작성 중",
  RECEIVED: "입고 완료",
  CANCELLED: "취소",
};

export type PurchaseSummary = {
  id: number;
  purchaseNo: string;
  purchasedOn: string;
  status: PurchaseStatus;
  supplierName: string | null;
  locationName: string;
  itemCount: number;
  totalQty: number;
  totalCost: number;
};

export type PurchaseItem = {
  skuId: number;
  skuCode: string;
  productName: string;
  optionText: string;
  grade: Grade;
  price: number;
  qty: number;
  unitCost: number;
};

export type PurchaseDetail = {
  id: number;
  purchaseNo: string;
  purchasedOn: string;
  status: PurchaseStatus;
  supplierId: number | null;
  supplierName: string | null;
  locationId: number;
  locationName: string;
  memo: string | null;
  totalCost: number;
  receivedAt: string | null;
  items: PurchaseItem[];
};

export type PurchaseForm = {
  supplierId: number | null;
  locationId: number;
  purchasedOn: string | null;
  memo: string | null;
  items: { skuId: number; qty: number; unitCost: number }[];
};

export const inventoryApi = {
  locations: () => request<Location[]>("/api/admin/locations"),
  stock: (params: { q?: string; locationId?: number | null; maxAvailable?: number | null; page?: number; size?: number }) => {
    const search = new URLSearchParams();
    if (params.q) search.set("q", params.q);
    if (params.locationId) search.set("locationId", String(params.locationId));
    if (params.maxAvailable != null) search.set("maxAvailable", String(params.maxAvailable));
    if (params.page) search.set("page", String(params.page));
    if (params.size) search.set("size", String(params.size));
    return request<Page<StockRow>>(`/api/admin/inventory?${search}`);
  },
  lookup: (code: string) => request<StockRow>(`/api/admin/inventory/lookup?code=${encodeURIComponent(code)}`),
  movements: (skuId: number) => request<Movement[]>(`/api/admin/inventory/${skuId}/movements`),
  adjust: (body: { skuId: number; locationId: number; qty: number; memo: string }) =>
    request<StockRow>("/api/admin/inventory/adjust", { method: "POST", body: JSON.stringify(body) }),
  transfer: (body: { skuId: number; fromLocationId: number; toLocationId: number; qty: number; memo: string | null }) =>
    request<StockRow>("/api/admin/inventory/transfer", { method: "POST", body: JSON.stringify(body) }),

  suppliers: (all = false) => request<Supplier[]>(`/api/admin/suppliers${all ? "?all=true" : ""}`),
  createSupplier: (body: SupplierForm) =>
    request<{ id: number }>("/api/admin/suppliers", { method: "POST", body: JSON.stringify(body) }),
  updateSupplier: (id: number, body: SupplierForm) =>
    request<void>(`/api/admin/suppliers/${id}`, { method: "PUT", body: JSON.stringify(body) }),

  purchases: (params: { status?: string; q?: string; page?: number }) => {
    const search = new URLSearchParams();
    if (params.status) search.set("status", params.status);
    if (params.q) search.set("q", params.q);
    if (params.page) search.set("page", String(params.page));
    return request<Page<PurchaseSummary>>(`/api/admin/purchases?${search}`);
  },
  purchase: (id: number) => request<PurchaseDetail>(`/api/admin/purchases/${id}`),
  createPurchase: (body: PurchaseForm) =>
    request<PurchaseDetail>("/api/admin/purchases", { method: "POST", body: JSON.stringify(body) }),
  updatePurchase: (id: number, body: PurchaseForm) =>
    request<PurchaseDetail>(`/api/admin/purchases/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  receivePurchase: (id: number) => request<PurchaseDetail>(`/api/admin/purchases/${id}/receive`, { method: "POST" }),
  cancelPurchase: (id: number) => request<PurchaseDetail>(`/api/admin/purchases/${id}/cancel`, { method: "POST" }),
};

// ------------------------------------------------------------------
// 배송·송장
// ------------------------------------------------------------------

export type ReadyOrder = {
  orderNo: string;
  paidAt: string | null;
  status: string;
  receiverName: string | null;
  itemsText: string;
  totalQty: number;
  payAmount: number;
};

export type ShipmentRow = {
  id: number;
  orderNo: string;
  receiverName: string | null;
  carrierName: string | null;
  trackingNo: string | null;
  trackingUrl: string | null;
  status: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  itemsText: string;
};

export const SHIPMENT_STATUS_LABEL: Record<string, string> = {
  READY: "출고 준비",
  SHIPPED: "배송중",
  IN_TRANSIT: "배송중",
  OUT_FOR_DELIVERY: "배송 출발",
  DELIVERED: "배송완료",
  FAILED: "배송 실패",
  CANCELLED: "취소",
};

export type ImportResult = {
  success: number;
  failed: number;
  lines: { row: number; orderNo: string; trackingNo: string; ok: boolean; message: string }[];
};

export type SenderSettings = {
  senderName: string | null;
  senderPhone: string | null;
  shipFromZipcode: string | null;
  shipFromAddress: string | null;
  returnZipcode: string | null;
  returnAddress: string | null;
};

/** 파일 내려받기 (POST + CSRF). 응답 헤더의 파일 이름으로 저장한다 */
async function downloadPost(path: string, body: unknown, fallbackName: string) {
  const token = await csrfToken(false);
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { "X-XSRF-TOKEN": token } : {}) },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => null);
    throw new ApiError(res.status, err?.detail ?? `내려받지 못했습니다. (${res.status})`, err?.code);
  }
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const name = encoded ? decodeURIComponent(encoded) : fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export const shippingApi = {
  newOrders: () => request<ReadyOrder[]>("/api/admin/shipping/new-orders"),
  ready: () => request<ReadyOrder[]>("/api/admin/shipping/ready"),
  accept: (orderNos: string[]) =>
    request<{ success: number; failed: number; errors: string[] }>("/api/admin/shipping/accept", {
      method: "POST",
      body: JSON.stringify({ orderNos }),
    }),
  exportSheet: (orderNos: string[]) => downloadPost("/api/admin/shipping/export", { orderNos }, "송장.xlsx"),
  importTracking: (file: File, carrierId: number | null) => {
    const form = new FormData();
    form.append("file", file);
    const query = carrierId ? `?carrierId=${carrierId}` : "";
    return request<ImportResult>(`/api/admin/shipping/import${query}`, { method: "POST", body: form });
  },
  shipments: (params: { status?: string; q?: string; page?: number }) => {
    const search = new URLSearchParams();
    if (params.status) search.set("status", params.status);
    if (params.q) search.set("q", params.q);
    if (params.page) search.set("page", String(params.page));
    return request<Page<ShipmentRow>>(`/api/admin/shipping/shipments?${search}`);
  },
  delivered: (id: number) => request<void>(`/api/admin/shipping/shipments/${id}/delivered`, { method: "POST" }),
  sender: () => request<SenderSettings>("/api/admin/shipping/sender"),
  updateSender: (body: SenderSettings) =>
    request<SenderSettings>("/api/admin/shipping/sender", { method: "PUT", body: JSON.stringify(body) }),
};

// ------------------------------------------------------------------
// 상품문의·구매후기
// ------------------------------------------------------------------

export type AdminQuestion = {
  id: number;
  productId: number;
  productName: string;
  author: string;
  question: string;
  secret: boolean;
  answer: string | null;
  answeredAt: string | null;
  createdAt: string;
};

export type AdminReview = {
  id: number;
  productId: number;
  productName: string;
  author: string;
  rating: number;
  content: string;
  hidden: boolean;
  createdAt: string;
};

export const communityAdminApi = {
  questions: (unanswered: boolean, page = 0) =>
    request<Page<AdminQuestion>>(`/api/admin/questions?unanswered=${unanswered}&page=${page}`),
  answer: (id: number, answer: string) =>
    request<void>(`/api/admin/questions/${id}/answer`, { method: "POST", body: JSON.stringify({ answer }) }),
  reviews: (page = 0) => request<Page<AdminReview>>(`/api/admin/reviews?page=${page}`),
  setHidden: (id: number, hidden: boolean) =>
    request<void>(`/api/admin/reviews/${id}/hidden`, { method: "POST", body: JSON.stringify({ hidden }) }),
};

// ------------------------------------------------------------------
// 사업자 회원
// ------------------------------------------------------------------

export type BusinessApplication = {
  userId: number;
  email: string | null;
  userName: string;
  businessNo: string;
  businessName: string;
  ceoName: string;
  businessItem: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED";
  rejectReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
};

export const businessAdminApi = {
  applications: (status: string, page = 0) =>
    request<Page<BusinessApplication>>(`/api/admin/business-members?status=${status}&page=${page}`),
  review: (userId: number, approve: boolean, rejectReason: string | null) =>
    request<void>(`/api/admin/business-members/${userId}/review`, {
      method: "POST",
      body: JSON.stringify({ approve, rejectReason }),
    }),
  updateProductTerms: (
    productId: number,
    body: { businessOnly: boolean; imageReuseAllowed: boolean; skuPrices: { skuId: number; businessPrice: number | null }[] },
  ) => request<AdminProductDetail>(`/api/admin/products/${productId}/business`, { method: "PUT", body: JSON.stringify(body) }),
};

// ------------------------------------------------------------------
// 사진으로 상품 정보 자동 입력 (Claude)
// ------------------------------------------------------------------

export type AiDraft = {
  name: string;
  brand: string;
  modelName: string;
  /** 0이면 알맞은 카테고리 없음 */
  categoryId: number;
  conditionGrade: GradeCode;
  conditionNote: string;
  options: string;
  description: string;
  /** 0이면 사진에 정가가 보이지 않음 */
  listPrice: number;
  barcode: string;
  kcCertNo: string;
  notice: { key: string; value: string }[];
  /** 저장 전에 직접 확인할 점 */
  warnings: string[];
};

export const aiApi = {
  config: () => request<{ enabled: boolean; maxPhotos: number }>("/api/admin/products/ai-draft"),
  draft: (photos: Blob[], hint: string) => {
    const form = new FormData();
    photos.forEach((p, i) => form.append("photos", p, `photo-${i + 1}.jpg`));
    if (hint.trim()) form.append("hint", hint.trim());
    return request<AiDraft>("/api/admin/products/ai-draft", { method: "POST", body: form });
  },
};

// ------------------------------------------------------------------
// 회원 관리
// ------------------------------------------------------------------

export type JoinMethod = "EMAIL" | "KAKAO" | "NAVER" | "APPLE";
export type MemberStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";

export const JOIN_METHOD_LABEL: Record<JoinMethod, string> = {
  EMAIL: "이메일",
  KAKAO: "카카오",
  NAVER: "네이버",
  APPLE: "애플",
};

export const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: "정상",
  SUSPENDED: "정지",
  WITHDRAWN: "탈퇴",
};

export const BUSINESS_STATUS_LABEL: Record<string, string> = {
  PENDING: "사업자 심사중",
  APPROVED: "사업자",
  REJECTED: "사업자 거절",
};

export type MemberRow = {
  id: number;
  name: string;
  email: string | null;
  joinMethods: JoinMethod[];
  phoneMasked: string | null;
  status: MemberStatus;
  businessStatus: string | null;
  createdAt: string;
  lastLoginAt: string | null;
};

export type MemberDetail = {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  joinMethods: JoinMethod[];
  status: MemberStatus;
  createdAt: string;
  lastLoginAt: string | null;
  withdrawnAt: string | null;
  marketingAgreed: boolean;
  marketingAgreedAt: string | null;
  agreements: { type: "TERMS" | "PRIVACY" | "MARKETING" | "AGE_14"; version: string; agreed: boolean; createdAt: string }[];
  business: {
    status: string;
    businessNo: string | null;
    businessName: string | null;
    ceoName: string | null;
    businessItem: string | null;
    rejectReason: string | null;
    createdAt: string | null;
    reviewedAt: string | null;
  } | null;
  orders: { count: number; totalAmount: number; lastOrderedAt: string | null };
  recentOrders: { orderNo: string; status: string; payAmount: number; orderedAt: string }[];
  statusLogs: { action: "SUSPEND_USER" | "UNSUSPEND_USER"; reason: string | null; actorName: string | null; createdAt: string }[];
};

export type MemberQuery = { q?: string; method?: string; status?: string; from?: string; to?: string; page?: number };

export const memberApi = {
  list: (params: MemberQuery) => {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value) search.set(key, String(value));
    }
    return request<Page<MemberRow>>(`/api/admin/members?${search}`);
  },
  detail: (id: number) => request<MemberDetail>(`/api/admin/members/${id}`),
  changeStatus: (id: number, status: "ACTIVE" | "SUSPENDED", reason: string) =>
    request<MemberDetail>(`/api/admin/members/${id}/status`, {
      method: "POST",
      body: JSON.stringify({ status, reason }),
    }),
};

// ------------------------------------------------------------------
// 운영자 계정 (관리자만)
// ------------------------------------------------------------------

export type StaffRole = "ADMIN" | "OPERATOR";

export const STAFF_ROLE_LABEL: Record<StaffRole, string> = {
  ADMIN: "관리자",
  OPERATOR: "운영자",
};

export type Staff = {
  id: number;
  name: string;
  email: string;
  role: StaffRole;
  status: "ACTIVE" | "SUSPENDED";
  createdAt: string;
  lastLoginAt: string | null;
};

export const staffApi = {
  list: () => request<Staff[]>("/api/admin/staff"),
  create: (body: { email: string; name: string; password: string; role: StaffRole }) =>
    request<Staff[]>("/api/admin/staff", { method: "POST", body: JSON.stringify(body) }),
  change: (id: number, body: { role?: StaffRole; status?: "ACTIVE" | "SUSPENDED" }) =>
    request<Staff[]>(`/api/admin/staff/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  resetPassword: (id: number, password: string) =>
    request<void>(`/api/admin/staff/${id}/password`, { method: "POST", body: JSON.stringify({ password }) }),
};
