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
};

export type AdminSku = {
  id: number;
  skuCode: string;
  optionValues: Record<string, string>;
  grade: Grade;
  conditionNote: string | null;
  barcode: string | null;
  price: number;
  listPrice: number | null;
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
  skus: AdminSku[];
  images: ProductImage[];
};

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
  shippingFee: number;
  payAmount: number;
  payment: { method: string | null; easyPayProvider: string | null; amount: number; balanceAmount: number; status: string; approvedAt: string | null } | null;
  shipments: { id: number; carrierName: string; trackingNo: string; status: string; shippedAt: string | null }[];
  cancellable: boolean;
};

export type Carrier = { id: number; code: string; name: string };

export const ITEM_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: "결제대기",
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
      optionValues: Record<string, string>;
      conditionNote: string | null;
      barcode: string | null;
      price: number;
      listPrice: number | null;
      status: SkuStatus;
    },
  ) => request<void>(`/api/admin/skus/${skuId}`, { method: "PUT", body: JSON.stringify(body) }),
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
