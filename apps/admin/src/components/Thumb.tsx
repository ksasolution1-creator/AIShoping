/* eslint-disable @next/next/no-img-element -- 업로드 사진은 API 서버(/files)에서 받는다 */

export function Thumb({ src, className = "size-12" }: { src: string | null; className?: string }) {
  if (!src) {
    return (
      <div className={`flex shrink-0 items-center justify-center rounded bg-surface-muted text-[10px] text-ink-faint ${className}`}>
        사진 없음
      </div>
    );
  }
  return <img src={src} alt="" className={`shrink-0 rounded bg-surface-muted object-cover ${className}`} />;
}
