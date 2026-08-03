package com.pokade.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeStatus;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class AiGradeServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private S3UploadService s3UploadService;

    @Mock
    private ImageQualityChecker imageQualityChecker;

    @Mock
    private GradeResultRepository gradeResultRepository;

    @Mock
    private GradeResultImageRepository gradeResultImageRepository;

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
}
