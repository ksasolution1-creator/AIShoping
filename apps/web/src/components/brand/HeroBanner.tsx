import { WhaleMark } from "./WhaleMark";

const KEYWORDS = ["도매", "재고상품", "반값특가", "대량구매"];

/** 메인 배너: 바다 위 고래와 떠다니는 가격표 (커버 이미지 분위기) */
export function HeroBanner() {
  return (
    <section
      aria-label="떨고래 마켓 소개"
      className="relative overflow-hidden rounded-3xl bg-gradient-to-b from-sea to-[#0b3d91] px-6 py-8 text-white sm:px-10 sm:py-10"
    >
      {/* 물방울 */}
      <span aria-hidden className="absolute top-6 left-[46%] size-10 rounded-full bg-white/15" />
      <span aria-hidden className="absolute -right-6 -bottom-8 size-32 rounded-full bg-white/10" />
      <span aria-hidden className="absolute bottom-5 left-[42%] size-5 rounded-full bg-white/20" />

      <div className="relative grid items-center gap-6 sm:grid-cols-[1.2fr_1fr]">
        <div className="flex flex-col gap-3">
          <p className="font-display text-lg text-tag sm:text-xl">좋은 상품, 더 착한 가격!</p>
          <h1 className="font-display text-4xl leading-tight [paint-order:stroke] [-webkit-text-stroke:6px_var(--color-navy)] sm:text-6xl">
            떨고래 마켓
          </h1>
          <p className="max-w-md text-sm text-foam sm:text-base">
            검수한 재고·반품·리퍼 상품을 상태등급과 실물 사진 그대로. 큰손 고래가 대량으로 사 와서 땡처리 가격에 풉니다.
          </p>
          <ul className="mt-1 flex flex-wrap gap-2 text-sm font-bold">
            {KEYWORDS.map((word) => (
              <li key={word} className="rounded-full bg-white/15 px-3 py-1 ring-1 ring-white/30">
                {word}
              </li>
            ))}
          </ul>
        </div>

        <div aria-hidden className="relative mx-auto h-44 w-full max-w-xs sm:h-56">
          <PriceTag className="top-1 left-2 -rotate-12 bg-tag text-[#c2185b]">-70%</PriceTag>
          <PriceTag className="top-4 right-0 rotate-12 bg-tag text-[#c2185b]">땡처리</PriceTag>
          <PriceTag className="bottom-14 left-0 rotate-6 bg-sale text-white">재고</PriceTag>
          <PriceTag className="right-2 bottom-16 -rotate-6 bg-sale text-white">도매</PriceTag>
          <WhaleMark className="animate-bob absolute inset-x-0 top-3 mx-auto h-36 w-36 drop-shadow-[0_10px_20px_rgba(10,46,110,0.45)] sm:h-44 sm:w-44" />
          {/* 크라프트 박스 */}
          <div className="absolute inset-x-6 bottom-0 flex justify-center gap-1.5">
            {["대량구매", "떨고래", "특가"].map((label, i) => (
              <span
                key={label}
                className={`relative flex items-center justify-center rounded-md border-2 border-[#9c7650] bg-kraft font-display text-sm text-navy shadow-md ${
                  i === 1 ? "h-14 w-24 text-base" : "mt-3 h-11 w-16"
                }`}
              >
                <span className="absolute inset-x-0 top-0 mx-auto h-2 w-4/5 rounded-b bg-[#e8d3b8]" />
                {label}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function PriceTag({ className, children }: { className: string; children: React.ReactNode }) {
  return (
    <span className={`absolute z-10 rounded-lg px-2.5 py-1 font-display text-base shadow-md ${className}`}>
      <span className="mr-1 inline-block size-1.5 rounded-full bg-white/80 align-middle" />
      {children}
    </span>
  );
}
