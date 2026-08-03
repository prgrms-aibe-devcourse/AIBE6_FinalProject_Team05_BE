package com.pokade.global.aop;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.UncheckedIOException;

/**
 * 서비스 계층에서 발생하는 저수준 인프라 예외(IO, DB, S3)를 BusinessException으로 변환한다.
 * ExceptionLoggingAspect보다 낮은 Order 값(더 바깥쪽)으로 두어, 로깅 Aspect가 먼저
 * 원본 예외를 기록한 뒤 이 Aspect가 최종적으로 변환하도록 한다.
 */
@Aspect
@Component
@Order(1)
public class ExceptionTranslationAspect {

    @Around("execution(* com.pokade.domain..service..*.*(..))")
    public Object translate(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (BusinessException e) {
            throw e;
        } catch (UncheckedIOException e) {
            throw new BusinessException(ErrorCode.FILE_IO_ERROR, e.getMessage());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.FILE_IO_ERROR, e.getMessage());
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, e.getMessage());
        }
    }
}
