/* eslint-disable @next/next/no-img-element -- 상품 사진은 API 서버(/files)에서 받는다. 운영에서 CDN을 붙이면 next/image로 바꾼다. */

export function ProductImage({
  src,
  alt,
  className = "",
}: {
  src: string | null;
  alt: string;
  className?: string;
}) {
  if (!src) {
    return (
      <div
        className={`flex items-center justify-center bg-surface-muted text-xs text-ink-faint ${className}`}
      >
        사진 준비중
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={alt}
      loading="lazy"
      className={`bg-surface-muted object-cover ${className}`}
    />
  );
}
