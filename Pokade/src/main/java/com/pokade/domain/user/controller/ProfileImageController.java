package com.pokade.domain.user.controller;

import com.pokade.domain.user.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    @GetMapping("/{userId}/profile/image")
    public ResponseEntity<Resource> getProfile(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return profileImageService.serve(userId, ifNoneMatch);
    }
}
