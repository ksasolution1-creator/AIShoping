// 브라우저에서 /api 를 호출할 때 쓴다 (Next.js가 API 서버로 프록시).
// 변경 요청(POST 등)에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 붙인다.

export type Me = {
  id: number;
  email: string | null;
  name: string;
  type: "CUSTOMER" | "STAFF";
  roles: string[];
  sellerId: number | null;
};

export class ClientApiError extends Error {
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

async function csrfToken(refresh = false) {
  let token = refresh ? null : readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const token = await csrfToken(retried);
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let res: Response;
  try {
    res = await fetch(path, { ...init, method, headers, credentials: "same-origin" });
  } catch {
    throw new ClientApiError(0, "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    // 토큰이 만료됐으면 새로 받아서 한 번만 다시 보낸다
    if (res.status === 403 && body?.code === "CSRF" && !retried) {
      return apiRequest<T>(path, init, true);
    }
    throw new ClientApiError(
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

export const authApi = {
  me: async (): Promise<Me | null> => {
    try {
      return await apiRequest<Me>("/api/auth/me");
    } catch (e) {
      if (e instanceof ClientApiError && e.status === 401) return null;
      throw e;
    }
  },
  login: (email: string, password: string) =>
    apiRequest<Me>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  signup: (body: {
    email: string;
    password: string;
    name: string;
    agreeAge14: boolean;
    agreeTerms: boolean;
    agreePrivacy: boolean;
    agreeMarketing: boolean;
  }) => apiRequest<Me>("/api/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  logout: () => apiRequest<void>("/api/auth/logout", { method: "POST" }),
  withdraw: (password: string) =>
    apiRequest<void>("/api/me/withdraw", { method: "POST", body: JSON.stringify({ password }) }),
};

// ----------------------------------------------------------------------
// 장바구니·주문
// ----------------------------------------------------------------------

export type CartItem = {
  skuId: number;
  productId: number;
  productName: string;
  optionText: string;
  grade: { code: string; label: string };
  thumbnailUrl: string | null;
  price: number;
  listPrice: number | null;
  qty: number;
  available: number;
  purchasable: boolean;
  lineTotal: number;
};

export type CartView = { items: CartItem[]; itemsTotal: number; shippingFee: number; payAmount: number };

export type OrderLine = { skuId: number; qty: number };

export type Preview = {
  lines: {
    skuId: number;
    productName: string;
    optionText: string;
    conditionGrade: string;
    thumbnailUrl: string | null;
    price: number;
    qty: number;
    available: number;
    purchasable: boolean;
    lineTotal: number;
  }[];
  itemsTotal: number;
  shippingFee: number;
  payAmount: number;
  allPurchasable: boolean;
};

export type CheckoutInfo = {
  orderNo: string;
  orderName: string;
  amount: number;
  customerKey: string;
  customerName: string;
  customerEmail: string | null;
  clientKey: string;
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
  orderedAt: string;
  paidAt: string | null;
  status: string;
  ordererName: string;
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
  discountTotal: number;
  payAmount: number;
  payment: {
    method: string | null;
    easyPayProvider: string | null;
    amount: number;
    balanceAmount: number;
    status: string;
    approvedAt: string | null;
  } | null;
  shipments: { id: number; carrierName: string; trackingNo: string; status: string; shippedAt: string | null }[];
  cancellable: boolean;
  claims: Claim[];
};

export type Claim = {
  id: number;
  type: "CANCEL" | "RETURN" | "EXCHANGE";
  status: "REQUESTED" | "APPROVED" | "COLLECTING" | "INSPECTING" | "COMPLETED" | "REJECTED" | "WITHDRAWN";
  orderNo: string;
  orderItemId: number;
  productName: string;
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
  requestedAt: string;
  completedAt: string | null;
};

export const RETURN_REASONS = [
  { code: "CHANGE_OF_MIND", label: "단순 변심", customerPays: true },
  { code: "DEFECTIVE", label: "상품 불량·파손", customerPays: false },
  { code: "NOT_AS_DESCRIBED", label: "상품 설명·상태등급과 다름", customerPays: false },
  { code: "WRONG_ITEM", label: "다른 상품이 옴", customerPays: false },
  { code: "MISSING_PARTS", label: "구성품 누락", customerPays: false },
] as const;

export const CLAIM_STATUS_LABEL: Record<Claim["status"], string> = {
  REQUESTED: "신청 접수",
  APPROVED: "승인",
  COLLECTING: "회수 중",
  INSPECTING: "검수 중",
  COMPLETED: "완료",
  REJECTED: "거절",
  WITHDRAWN: "철회",
};

export const claimApi = {
  cancelItem: (orderNo: string, itemId: number) =>
    apiRequest<Claim>(`/api/orders/${orderNo}/items/${itemId}/cancel`, { method: "POST", body: "{}" }),
  requestReturn: (orderNo: string, itemId: number, reason: string, detail: string) =>
    apiRequest<Claim>(`/api/orders/${orderNo}/items/${itemId}/return`, {
      method: "POST",
      body: JSON.stringify({ reason, detail }),
    }),
  withdraw: (claimId: number) => apiRequest<Claim>(`/api/claims/${claimId}/withdraw`, { method: "POST" }),
};

export type Page<T> = { items: T[]; page: number; size: number; total: number; totalPages: number };

export const cartApi = {
  view: () => apiRequest<CartView>("/api/cart"),
  add: (skuId: number, qty: number) =>
    apiRequest<CartView>("/api/cart/items", { method: "POST", body: JSON.stringify({ skuId, qty }) }),
  changeQty: (skuId: number, qty: number) =>
    apiRequest<CartView>(`/api/cart/items/${skuId}`, { method: "PATCH", body: JSON.stringify({ qty }) }),
  remove: (skuIds: number[]) => apiRequest<CartView>(`/api/cart/items?skuIds=${skuIds.join(",")}`, { method: "DELETE" }),
};

export const orderApi = {
  config: () =>
    apiRequest<{ paymentReady: boolean; clientKey: string; customerKey: string }>("/api/checkout/config"),
  preview: (items: OrderLine[]) =>
    apiRequest<Preview>("/api/checkout/preview", { method: "POST", body: JSON.stringify({ items }) }),
  create: (body: {
    items: OrderLine[];
    receiverName: string;
    receiverPhone: string;
    zipcode: string;
    address1: string;
    address2: string;
    deliveryMemo: string;
  }) => apiRequest<CheckoutInfo>("/api/orders", { method: "POST", body: JSON.stringify(body) }),
  confirm: (paymentKey: string, orderId: string, amount: number) =>
    apiRequest<OrderDetail>("/api/payments/confirm", {
      method: "POST",
      body: JSON.stringify({ paymentKey, orderId, amount }),
    }),
  abandon: (orderNo: string) => apiRequest<void>(`/api/orders/${orderNo}/abandon`, { method: "POST" }),
  list: (page = 0) => apiRequest<Page<OrderSummary>>(`/api/orders?page=${page}`),
  detail: (orderNo: string) => apiRequest<OrderDetail>(`/api/orders/${orderNo}`),
  cancel: (orderNo: string, reason: string) =>
    apiRequest<OrderDetail>(`/api/orders/${orderNo}/cancel`, { method: "POST", body: JSON.stringify({ reason }) }),
};

/** 주문 상품 상태 이름 */
export const ITEM_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: "결제대기",
  PAID: "결제완료",
  PREPARING: "상품준비중",
  SHIPPED: "배송중",
  DELIVERED: "배송완료",
  CONFIRMED: "구매확정",
  CANCEL_REQUESTED: "취소요청",
  CANCELLED: "취소완료",
  RETURN_REQUESTED: "반품요청",
  RETURNING: "반품회수중",
  RETURNED: "반품완료",
  EXCHANGE_REQUESTED: "교환요청",
  EXCHANGING: "교환중",
  EXCHANGED: "교환완료",
};

/** 로그인이 필요한 요청에서 401이면 로그인 화면으로 보낸다 */
export function loginUrl() {
  return `/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`;
}

/** 로그인 후 돌아갈 주소. 다른 사이트로 보내는 주소는 무시한다 */
export function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

const GRADE_LABEL: Record<string, string> = {
  NEW: "새상품",
  RETURN_UNOPENED: "반품 미개봉",
  RETURN_OPENED: "반품 개봉",
  REFURB: "리퍼",
  USED_A: "중고 A급",
  USED_B: "중고 B급",
  USED_C: "중고 C급",
  DISPLAY: "전시상품",
};

/** 상태등급 코드 → 이름 (예: USED_B → 중고 B급) */
export function gradeLabel(code: string | null | undefined) {
  if (!code) return null;
  return GRADE_LABEL[code] ?? code;
}
