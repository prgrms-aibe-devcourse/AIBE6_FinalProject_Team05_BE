package com.pokade.domain.user.controller;

import com.pokade.domain.user.service.ProfileImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원", description = "내 정보·프로필·비밀번호·마케팅 동의·회원탈퇴 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    @Operation(
            summary = "프로필 이미지 조회",
            description = "회원의 프로필 이미지를 서버가 프록시해서 내려줍니다. 비로그인 상태에서도 호출할 수 있습니다. "
                    + "응답에 ETag를 함께 내려주며, If-None-Match 헤더가 일치하면 본문 없이 304를 반환합니다."
    )
    @GetMapping("/{userId}/profile/image")
    public ResponseEntity<Resource> getProfile(
            @Parameter(description = "회원 ID") @PathVariable Long userId,
            @Parameter(description = "캐시 검증용 ETag") @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return profileImageService.serve(userId, ifNoneMatch);
    }
}
