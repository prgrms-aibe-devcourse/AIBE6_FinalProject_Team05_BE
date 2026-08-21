package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.UserAgreement;
import com.pokade.domain.user.entity.type.AgreementType;
import com.pokade.domain.user.repository.UserAgreementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserAgreementServiceTest {

    @Mock
    UserAgreementRepository userAgreementRepository;
    @InjectMocks
    UserAgreementService userAgreementService;

    @Test
    @DisplayName("가입 동의를 항목 수만큼 행으로 남기고 현재 약관 버전을 붙인다")
    void recordSignupAgreements_savesOneRowPerType() {
        // when
        userAgreementService.recordSignupAgreements(1L, Map.of(
                AgreementType.TERMS_OF_SERVICE, true,
                AgreementType.PRIVACY_POLICY, true,
                AgreementType.THIRD_PARTY_SHARING, true,
                AgreementType.MARKETING, false));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> captor = ArgumentCaptor.forClass(List.class);
        then(userAgreementRepository).should().saveAll(captor.capture());

        List<UserAgreement> rows = captor.getValue();
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo(1L);
            assertThat(row.getVersion()).isEqualTo(UserAgreement.CURRENT_VERSION);
        });
        assertThat(rows).filteredOn(r -> r.getType() == AgreementType.MARKETING)
                .singleElement()
                .satisfies(r -> assertThat(r.isAgreed()).isFalse());
    }

    @Test
    @DisplayName("마케팅 동의 변경은 덮어쓰지 않고 새 행으로 쌓는다")
    void changeMarketing_appendsNewRow() {
        // when
        userAgreementService.changeMarketing(1L, true);

        // then
        ArgumentCaptor<UserAgreement> captor = ArgumentCaptor.forClass(UserAgreement.class);
        then(userAgreementRepository).should().save(captor.capture());

        UserAgreement row = captor.getValue();
        assertThat(row.getUserId()).isEqualTo(1L);
        assertThat(row.getType()).isEqualTo(AgreementType.MARKETING);
        assertThat(row.isAgreed()).isTrue();
        assertThat(row.getVersion()).isEqualTo(UserAgreement.CURRENT_VERSION);
    }

    @Test
    @DisplayName("마케팅 동의 여부는 항목별 최신 행을 따른다")
    void isMarketingAgreed_readsLatestRow() {
        // given - 최신 행이 철회(false)인 상태
        given(userAgreementRepository.findFirstByUserIdAndTypeOrderByAgreedAtDescIdDesc(
                eq(1L), eq(AgreementType.MARKETING)))
                .willReturn(Optional.of(UserAgreement.record(
                        1L, AgreementType.MARKETING, false, LocalDateTime.now())));

        // when & then
        assertThat(userAgreementService.isMarketingAgreed(1L)).isFalse();
    }

    @Test
    @DisplayName("동의 이력이 없으면 동의하지 않은 것으로 본다")
    void isMarketingAgreed_noHistory_returnsFalse() {
        // given - 이관 누락 등으로 행이 아예 없는 경우
        given(userAgreementRepository.findFirstByUserIdAndTypeOrderByAgreedAtDescIdDesc(any(), any()))
                .willReturn(Optional.empty());

        // when & then
        assertThat(userAgreementService.isMarketingAgreed(1L)).isFalse();
    }
}
