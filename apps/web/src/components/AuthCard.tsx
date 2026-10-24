export function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto flex w-full max-w-sm flex-col gap-6 py-10">
      <h1 className="text-center text-xl font-bold">{title}</h1>
      {children}
    </div>
  );
}

export function TextField({
  id,
  label,
  error,
  ...input
}: { id: string; label: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        name={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="h-11 rounded-md border border-line px-3 text-sm outline-none focus:border-brand aria-invalid:border-sale"
        {...input}
      />
      {error && (
        <p id={`${id}-error`} className="text-xs text-sale">
          {error}
        </p>
      )}
    </div>
  );
}

/** 소셜 로그인 자리. 각 개발자 콘솔에서 앱 키를 받으면 연결한다 */
export function SocialLoginPlaceholder() {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3 text-xs text-ink-faint">
        <span className="h-px flex-1 bg-line" />
        간편 로그인
        <span className="h-px flex-1 bg-line" />
      </div>
      {["카카오", "네이버", "Apple"].map((provider) => (
        <button
          key={provider}
          type="button"
          disabled
          className="h-11 rounded-md border border-line text-sm text-ink-faint"
          title="준비중입니다"
        >
          {provider}로 계속하기 (준비중)
        </button>
      ))}
    </div>
  );
}
