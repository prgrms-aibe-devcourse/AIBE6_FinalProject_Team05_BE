package com.pokade.global.exception;

import com.pokade.domain.ai.service.AiGradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 멀티파트 오류 - 사진 누락 등 (400)
    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipart(MultipartException e) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("파일 업로드 오류");
        detail.setDetail("사진 6장(앞면, 뒷면, 모서리 4장)이 모두 필요합니다.");
        return detail;
    }

    // 포인트 부족 (402)
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException e) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.PAYMENT_REQUIRED);
        detail.setTitle("포인트 부족");
        detail.setDetail(e.getMessage());
        return detail;
    }

    // AI 서비스 오류 (503)
    @ExceptionHandler(AiGradeService.AiServiceUnavailableException.class)
    public ProblemDetail handleAiUnavailable(AiGradeService.AiServiceUnavailableException e) {
        log.error("AI 서비스 일시 불가", e);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        detail.setTitle("AI 서비스 일시 중단");
        detail.setDetail(e.getMessage());
        return detail;
    }

    // 잘못된 요청 파라미터 (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArg(IllegalArgumentException e) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("잘못된 요청");
        detail.setDetail(e.getMessage());
        return detail;
    }
}
