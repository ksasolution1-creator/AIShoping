package com.aishop.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

	private AuthRequests() {
	}

	public record Signup(
			@NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 100) String email,
			@NotBlank(message = "비밀번호를 입력해 주세요.")
			@Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
			@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요.")
			String password,
			@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 30) String name,
			@NotNull(message = "만 14세 이상만 가입할 수 있습니다.")
			@AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.") Boolean agreeAge14,
			@NotNull(message = "이용약관에 동의해 주세요.")
			@AssertTrue(message = "이용약관에 동의해 주세요.") Boolean agreeTerms,
			@NotNull(message = "개인정보 수집·이용에 동의해 주세요.")
			@AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.") Boolean agreePrivacy,
			Boolean agreeMarketing) {

		public boolean marketingAgreed() {
			return Boolean.TRUE.equals(agreeMarketing);
		}

	}

	public record Login(
			@NotBlank(message = "이메일을 입력해 주세요.") String email,
			@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
	}

	public record Withdraw(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
	}

	public record ChangePassword(
			@NotBlank(message = "현재 비밀번호를 입력해 주세요.") String currentPassword,
			@NotBlank(message = "새 비밀번호를 입력해 주세요.")
			@Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
			@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요.")
			String newPassword) {
	}

}
