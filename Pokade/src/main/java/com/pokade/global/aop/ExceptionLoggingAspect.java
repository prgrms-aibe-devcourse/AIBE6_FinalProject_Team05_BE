package com.pokade.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

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
        log.error("{}.{}() 예외 발생 - args={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()),
                ex);
    }
}
