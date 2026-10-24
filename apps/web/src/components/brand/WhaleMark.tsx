/** 떨고래 마스코트 (ddeolgorae_cover.svg의 고래를 잘라낸 것) */
export function WhaleMark({ className = "size-10", title }: { className?: string; title?: string }) {
  return (
    <svg
      viewBox="310 190 600 680"
      className={className}
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <g fill="#BFE6FF">
        <path d="M600 330 q-10 -60 -60 -80 q50 10 60 50 q10 -40 60 -50 q-50 20 -60 80z" />
        <circle cx="530" cy="240" r="16" />
        <circle cx="670" cy="240" r="16" />
        <circle cx="600" cy="215" r="18" />
      </g>
      <path
        d="M330 560 C330 410 460 340 600 340 C780 340 900 440 900 570 C900 660 840 720 760 740 L860 800 C890 820 880 860 845 855 L720 760 C680 770 640 775 600 775 C440 775 330 690 330 560 Z"
        fill="#1E5BC6"
        stroke="#0A2E6E"
        strokeWidth="10"
      />
      <path
        d="M370 600 C420 700 520 740 620 735 C720 730 800 690 840 640 C760 680 660 700 560 690 C470 680 410 650 370 600 Z"
        fill="#DDF0FF"
      />
      <circle cx="500" cy="530" r="26" fill="#0A2E6E" />
      <circle cx="508" cy="520" r="9" fill="#fff" />
      <ellipse cx="455" cy="590" rx="28" ry="16" fill="#FF8FA3" opacity="0.8" />
      <path d="M520 600 q35 30 75 0" fill="none" stroke="#0A2E6E" strokeWidth="9" strokeLinecap="round" />
      <path d="M600 660 q40 60 110 50 q-30 -50 -110 -50z" fill="#174DAA" />
    </svg>
  );
}
