import { WhaleMark } from "./WhaleMark";

const SIZES = {
  sm: { whale: "size-12", bar: "w-24", text: "text-xs", pad: "py-6" },
  md: { whale: "size-20", bar: "w-36", text: "text-sm", pad: "py-12" },
  lg: { whale: "size-28", bar: "w-48", text: "text-base", pad: "py-20" },
} as const;

/** 고래가 물을 뿜으며 둥둥 떠 있고, 아래 파도 막대가 흐르는 로딩 표시 */
export function WhaleLoader({
  label = "불러오는 중이에요",
  size = "md",
  className = "",
}: {
  label?: string;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const s = SIZES[size];
  return (
    <div role="status" aria-live="polite" className={`flex flex-col items-center justify-center gap-3 ${s.pad} ${className}`}>
      <div className="relative">
        {/* 물방울 */}
        <span aria-hidden className="animate-spout absolute -top-1 left-[28%] size-1.5 rounded-full bg-[#7cc4ff]" />
        <span aria-hidden className="animate-spout absolute -top-2 left-1/2 size-2 rounded-full bg-[#7cc4ff] [animation-delay:200ms]" />
        <span aria-hidden className="animate-spout absolute -top-1 right-[28%] size-1.5 rounded-full bg-[#7cc4ff] [animation-delay:400ms]" />
        <WhaleMark className={`animate-swim ${s.whale}`} />
      </div>
      {/* 파도 로딩바 */}
      <div aria-hidden className={`relative h-2 overflow-hidden rounded-full bg-[#ddf0ff] ${s.bar}`}>
        <div className="animate-wave-bar absolute inset-y-0 w-2/5 rounded-full bg-gradient-to-r from-[#2e8bf0] to-[#1e5bc6]" />
      </div>
      <span className={`font-medium text-[#3d5480] ${s.text}`}>{label}</span>
    </div>
  );
}
