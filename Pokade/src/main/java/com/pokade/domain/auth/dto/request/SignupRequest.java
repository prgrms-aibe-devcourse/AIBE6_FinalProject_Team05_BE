package com.pokade.domain.auth.dto.request;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$",
                message = "비밀번호는 영문과 숫자를 포함하며 공백을 포함할 수 없습니다."
        )
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname,

        @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
        boolean termsOfService,

        @AssertTrue(message = "개인정보 수집·이용에 동의해야 가입할 수 있습니다.")
        boolean privacyPolicy,

        @AssertTrue(message = "제3자 정보제공에 동의해야 가입할 수 있습니다.")
        boolean thirdPartySharing,

        boolean marketing

) {
}
