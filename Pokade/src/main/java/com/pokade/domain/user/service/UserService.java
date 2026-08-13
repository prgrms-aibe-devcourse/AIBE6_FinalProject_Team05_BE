package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional(readOnly = true)
    public UserResponse getMyInfo(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updateNickname(Long userId, String newNickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 현재 닉네임과 동일하면 변경할 것이 없음 (중복검사 오탐, 쿨다운 소모 방지)
        if (user.getNickname().equals(newNickname)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!user.canChangeNickname(now)) {
            throw new BusinessException(ErrorCode.NICKNAME_CHANGE_LIMITED);
        }

        if (userRepository.existsByNickname(newNickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        try {
            user.changeNickname(newNickname, now);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.isLocalUser()) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.deleteAll(userId); // 비밀번호 변경 시 모든 리프레시 토큰 무효화
    }
}
