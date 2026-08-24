package com.pokade.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.pokade.global.infra.storage.S3FileStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import com.pokade.domain.ai.dto.GradeRequest;
import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeStatus;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class AiGradeServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private S3FileStorage s3FileStorage;

    @Mock
    private ImageQualityChecker imageQualityChecker;

    @Mock
    private GradeResultRepository gradeResultRepository;

    @Mock
    private GradeResultImageRepository gradeResultImageRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    // @Mock MeterRegistry는 counter()/timer() 등이 null을 돌려줘 생성자에서 NPE가 난다 - 반드시 @Spy +
    // 실제 SimpleMeterRegistry를 써야 한다(docs/monitoring.md 참고).
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private AiGradeService aiGradeService;

    @Test
    @DisplayName("본인의 진단 이력을 페이징 조회하면 최신순으로 매핑된 응답을 반환한다")
    void getGradeHistory_returnsMappedPage() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        GradeResult gradeResult = GradeResult.builder()
                .userId(userId)
                .status(GradeStatus.SUCCESS)
                .grade("A")
                .isFree(true)
                .pointUsed(0)
                .retryAllowed(false)
                .build();
        Page<GradeResult> page = new PageImpl<>(List.of(gradeResult), pageable, 1);

        given(gradeResultRepository.findByUserId(userId, pageable)).willReturn(page);

        Page<GradeResponse> result = aiGradeService.getGradeHistory(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).grade()).isEqualTo("A");
    }

    @Test
    @DisplayName("페이지 크기가 상한(100)을 초과하면 BusinessException(INVALID_INPUT)을 던진다")
    void getGradeHistory_throwsWhenPageSizeExceedsLimit() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> aiGradeService.getGradeHistory(userId, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(gradeResultRepository, never()).findByUserId(userId, pageable);
    }

    @Test
    @DisplayName("동일 이미지로 이미 SUCCESS 진단이 있으면 Vision을 다시 부르지 않고 " +
            "캐시된 결과를 무료로(remainingPoints=null) 그대로 반환한다")
    void grade_withDuplicateImage_returnsCachedResultWithoutUpload() {
        Long userId = 1L;
        MockMultipartFile file = new MockMultipartFile("front", "front.jpg", "image/jpeg", "same-bytes".getBytes());
        GradeRequest request = new GradeRequest(file, file, file, file, file, file, null);

        GradeResult cached = GradeResult.builder()
                .userId(userId)
                .status(GradeStatus.SUCCESS)
                .grade("S")
                .isFree(true)
                .pointUsed(0)
                .retryAllowed(false)
                .build();

        given(gradeResultRepository.findFirstByUserIdAndImageHashAndStatusOrderByCreatedAtDesc(
                eq(userId), anyString(), eq(GradeStatus.SUCCESS)))
                .willReturn(java.util.Optional.of(cached));
        given(gradeResultImageRepository.findByGradeResultId(cached.getId())).willReturn(List.of());

        GradeResponse response = aiGradeService.grade(userId, request);

        assertThat(response.grade()).isEqualTo("S");
        assertThat(response.remainingPoints()).isNull();
        assertThat(response.cached()).isTrue();
        // 캐시 히트면 그 뒤 단계(S3 업로드, Vision 호출)는 아예 실행되지 않아야 한다.
        verify(s3FileStorage, never()).upload(any(), anyString());
    }
}
