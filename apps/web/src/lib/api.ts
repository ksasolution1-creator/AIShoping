// 서버 컴포넌트에서 API 서버를 직접 호출한다.
const API_URL = process.env.API_URL ?? "http://localhost:18080";

export type GradeCode =
  | "NEW"
  | "RETURN_UNOPENED"
  | "RETURN_OPENED"
  | "REFURB"
  | "USED_A"
  | "USED_B"
  | "USED_C"
  | "DISPLAY";

export type Grade = { code: GradeCode; label: string };

export type Category = {
  id: number;
  parentId: number | null;
  name: string;
  sortOrder: number;
};

export type ProductCard = {
  id: number;
  name: string;
  brand: string | null;
  thumbnailUrl: string | null;
  minPrice: number;
  maxListPrice: number | null;
  grades: Grade[];
  availableQty: number;
  /** 사업자 회원 전용 상품 */
  businessOnly: boolean;
};

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type ProductImage = {
  id: number;
  skuId: number | null;
  url: string;
  sortOrder: number;
};

export type ShopSku = {
  id: number;
  optionValues: Record<string, string>;
  grade: Grade;
  conditionNote: string | null;
  price: number;
  listPrice: number | null;
  /** 사업자 인증 회원에게 적용하는 판매가 */
  businessPrice: number | null;
  availableQty: number;
};

export type PriceTier = { minQty: number; discountBp: number };

/** 도매 구매조건: 최소구매수량, 결제 후 출고까지 걸리는 날, 수량별 할인 */
export type WholesaleTerms = { minOrderQty: number; shipWithinDays: number; tiers: PriceTier[] };

/** 원산지·제조사·포장 부피/무게 */
export type ProductSpec = {
  origin: string | null;
  manufacturer: string | null;
  packageSize: string | null;
  packageWeightG: number | null;
};

export type ProductDetail = {
  id: number;
  name: string;
  brand: string | null;
  modelName: string | null;
  categoryId: number | null;
  description: string | null;
  noticeInfo: Record<string, string>;
  kcCertNo: string | null;
  /** 소비기한 yyyy-MM-dd (없으면 null) */
  expiryDate: string | null;
  shippingFee: number;
  freeShippingThreshold: number | null;
  businessOnly: boolean;
  /** 상세이미지를 재판매에 써도 되는지 */
  imageReuseAllowed: boolean;
  wholesale: WholesaleTerms;
  spec: ProductSpec;
  returnInfo: { returnZipcode: string | null; returnAddress: string | null; oneWayFee: number };
  categoryPath: { id: number; name: string }[];
  skus: ShopSku[];
  images: ProductImage[];
};

export type ProductSort = "NEWEST" | "PRICE_ASC" | "PRICE_DESC" | "BEST";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, { cache: "no-store" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body?.detail ?? `API 오류 (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function fetchCategories() {
  return get<Category[]>("/api/categories");
}

export function fetchProducts(params: {
  q?: string;
  categoryId?: number;
  sort?: ProductSort;
  /** 정가 대비 40% 이상 싼 상품만 (땡처리관) */
  ddaeng?: boolean;
  page?: number;
  size?: number;
}) {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.categoryId) search.set("categoryId", String(params.categoryId));
  if (params.sort) search.set("sort", params.sort);
  if (params.ddaeng) search.set("ddaeng", "true");
  if (params.page) search.set("page", String(params.page));
  search.set("size", String(params.size ?? 24));
  return get<Page<ProductCard>>(`/api/products?${search}`);
}

/** 최근 7일 인기검색어 */
export function fetchPopularKeywords() {
  return get<string[]>("/api/search/popular");
}

export function fetchProduct(id: number) {
  return get<ProductDetail>(`/api/products/${id}`);
}
