import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex flex-col items-center gap-3 py-24 text-center">
      <h1 className="text-lg font-bold">페이지를 찾을 수 없습니다</h1>
      <p className="text-sm text-ink-soft">주소가 바뀌었거나 잘못 입력되었을 수 있습니다.</p>
      <Link href="/" className="mt-2 text-sm text-brand underline underline-offset-4">
        홈으로
      </Link>
    </div>
  );
}
