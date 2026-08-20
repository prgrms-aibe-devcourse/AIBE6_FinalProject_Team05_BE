package com.pokade.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuth2RegisterRequest(
        @NotBlank(message = "가입 티켓은 필수입니다.")
        String ticket,

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