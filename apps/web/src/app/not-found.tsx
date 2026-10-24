import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex flex-col items-center gap-3 py-24 text-center">
      <h1 className="text-lg font-bold">찾는 상품이 없습니다</h1>
      <p className="text-sm text-ink-soft">판매가 끝났거나 주소가 잘못되었을 수 있습니다.</p>
      <Link href="/" className="mt-2 text-sm text-brand underline underline-offset-4">
        상품 목록으로
      </Link>
    </div>
  );
}
