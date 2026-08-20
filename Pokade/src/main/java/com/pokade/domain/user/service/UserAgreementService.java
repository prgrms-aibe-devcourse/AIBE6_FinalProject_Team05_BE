package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.UserAgreement;
import com.pokade.domain.user.entity.type.AgreementType;
import com.pokade.domain.user.repository.UserAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAgreementService {

    private final UserAgreementRepository userAgreementRepository;

    // 가입 시점의 동의를 항목별로 한 행씩 남긴다. 필수 항목 검증은 요청 DTO의 @AssertTrue가 이미 했다.
    @Transactional
    public void recordSignupAgreements(Long userId, Map<AgreementType, Boolean> agreements) {
        LocalDateTime now = LocalDateTime.now();
        List<UserAgreement> rows = agreements.entrySet().stream()
                .map(e -> UserAgreement.record(userId, e.getKey(), e.getValue(), now))
                .toList();
        userAgreementRepository.saveAll(rows);
    }

    // 현재 마케팅 수신 동의 여부, 이력이 없으면(이관 누락 등) 동의하지 않은 것으로 본다.
    @Transactional(readOnly = true)
    public boolean isMarketingAgreed(Long userId) {
        return userAgreementRepository.findFirstByUserIdAndTypeOrderByAgreedAtDescIdDesc(userId, AgreementType.MARKETING)
                .map(UserAgreement::isAgreed)
                .orElse(false);
    }

    // 마케팅 수신 동의를 변경한다 덮어쓰지 않고 새 행으로 쌓아 이력을 남긴다.
    @Transactional
    public void changeMarketing(Long userId, boolean agreed) {
        userAgreementRepository.save(
                UserAgreement.record(userId, AgreementType.MARKETING, agreed, LocalDateTime.now())
        );
    }
}
