"use client";

export type AgreementKey = "agreeAge14" | "agreeTerms" | "agreePrivacy" | "agreeMarketing";
export type Agreements = Record<AgreementKey, boolean>;

export const NO_AGREEMENTS: Agreements = {
  agreeAge14: false,
  agreeTerms: false,
  agreePrivacy: false,
  agreeMarketing: false,
};

function agreementList(collected: string) {
  return [
    { key: "agreeAge14", label: "만 14세 이상입니다", required: true },
    { key: "agreeTerms", label: "이용약관 동의", required: true, href: "/terms" },
    {
      key: "agreePrivacy",
      label: "개인정보 수집·이용 동의",
      required: true,
      detail: `수집 항목: ${collected}. 목적: 회원 식별, 주문·배송, 고객 상담. 보유 기간: 탈퇴 시까지 (거래 기록은 관련 법에 따라 5년).`,
      href: "/privacy",
    },
    {
      key: "agreeMarketing",
      label: "할인·이벤트 소식 받기",
      required: false,
      detail: "동의하지 않아도 가입할 수 있으며, 마이페이지에서 언제든 바꿀 수 있습니다.",
    },
  ] satisfies { key: AgreementKey; label: string; required: boolean; detail?: string; href?: string }[];
}

/** 필수 동의를 빠뜨린 항목의 오류 문구 */
export function missingAgreements(agreements: Agreements) {
  const errors: Record<string, string> = {};
  for (const a of agreementList("")) {
    if (a.required && !agreements[a.key]) errors[a.key] = "필수 항목입니다. 동의해 주세요.";
  }
  return errors;
}

/** 가입 약관 동의 (이메일 가입·간편로그인 가입 공통) */
export function AgreementFields({
  value,
  onChange,
  errors,
  collected,
}: {
  value: Agreements;
  onChange: (next: Agreements) => void;
  errors: Record<string, string>;
  /** 개인정보 수집 항목 안내 */
  collected: string;
}) {
  const list = agreementList(collected);
  const allChecked = list.every((a) => value[a.key]);
  return (
    <fieldset className="flex flex-col gap-2 rounded-md border border-line p-4">
      <legend className="sr-only">약관 동의</legend>
      <label className="flex items-center gap-2 border-b border-line pb-3 font-semibold">
        <input
          type="checkbox"
          checked={allChecked}
          onChange={(e) =>
            onChange({
              agreeAge14: e.target.checked,
              agreeTerms: e.target.checked,
              agreePrivacy: e.target.checked,
              agreeMarketing: e.target.checked,
            })
          }
          className="size-4 accent-brand"
        />
        전체 동의
      </label>
      {list.map((a) => (
        <div key={a.key} className="flex flex-col gap-1">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={value[a.key]}
              onChange={(e) => onChange({ ...value, [a.key]: e.target.checked })}
              className="size-4 accent-brand"
            />
            <span className={a.required ? "" : "text-ink-soft"}>
              [{a.required ? "필수" : "선택"}] {a.label}
            </span>
            {"href" in a && a.href && (
              <a
                href={a.href}
                target="_blank"
                rel="noreferrer"
                className="ml-auto shrink-0 text-xs text-ink-faint underline underline-offset-2"
              >
                전문 보기
              </a>
            )}
          </label>
          {"detail" in a && a.detail && <p className="pl-6 text-xs leading-relaxed text-ink-faint">{a.detail}</p>}
          {errors[a.key] && <p className="pl-6 text-xs text-sale">{errors[a.key]}</p>}
        </div>
      ))}
    </fieldset>
  );
}
