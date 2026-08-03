package com.pokade.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 서비스 계층에서 발생하는 모든 예외를 자동으로 로깅한다.
 * ExceptionTranslationAspect보다 높은 Order 값(더 안쪽)으로 두어, 이 Aspect가
 * 먼저 원본(변환 전) 예외를 스택트레이스와 함께 기록한다.
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class ExceptionLoggingAspect {

    @AfterThrowing(pointcut = "execution(* com.pokade.domain..service..*.*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        // 서비스 인자에는 비밀번호/토큰 등 민감정보가 포함될 수 있어 로그에 남기지 않는다
        log.error("{}.{}() 예외 발생",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex);
    }
}
