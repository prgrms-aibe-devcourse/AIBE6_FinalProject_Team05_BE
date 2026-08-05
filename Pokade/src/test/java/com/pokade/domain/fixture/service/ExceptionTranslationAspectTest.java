package com.pokade.domain.fixture.service;

import com.pokade.global.aop.ExceptionTranslationAspect;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExceptionTranslationAspect의 pointcut(com.pokade.domain..service..*.*)이 실제로
 * 매칭되는 패키지 구조(domain 하위의 service 패키지)에 더미 서비스를 두고 검증한다.
 */
class ExceptionTranslationAspectTest {

    interface FakeService {
        void throwIo();

        void throwDataAccess();

        void throwSdkException();

        void throwBusiness();

        void succeed();
    }

    static class FakeServiceImpl implements FakeService {
        @Override
        public void throwIo() {
            throw new UncheckedIOException("파일 읽기 실패", new java.io.IOException("boom"));
        }

        @Override
        public void throwDataAccess() {
            throw new DataAccessResourceFailureException("DB 연결 실패");
        }

        @Override
        public void throwSdkException() {
            throw SdkClientException.create("S3 업로드 실패");
        }

        @Override
        public void throwBusiness() {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        @Override
        public void succeed() {
        }
    }

    private FakeService proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new FakeServiceImpl());
        factory.addAspect(new ExceptionTranslationAspect());
        return factory.getProxy();
    }

    @Test
    @DisplayName("UncheckedIOException은 BusinessException(FILE_IO_ERROR)로 변환된다")
    void translatesUncheckedIOException() {
        assertThatThrownBy(() -> proxy().throwIo())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_IO_ERROR);
    }

    @Test
    @DisplayName("DataAccessException은 BusinessException(DATABASE_ERROR)로 변환된다")
    void translatesDataAccessException() {
        assertThatThrownBy(() -> proxy().throwDataAccess())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DATABASE_ERROR);
    }

    @Test
    @DisplayName("SdkException(AWS SDK)은 BusinessException(FILE_IO_ERROR)로 변환된다")
    void translatesSdkException() {
        assertThatThrownBy(() -> proxy().throwSdkException())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_IO_ERROR);
    }

    @Test
    @DisplayName("이미 BusinessException이면 그대로 통과한다")
    void passesThroughExistingBusinessException() {
        assertThatThrownBy(() -> proxy().throwBusiness())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }

    @Test
    @DisplayName("예외가 없으면 정상 동작한다")
    void passesThroughOnSuccess() {
        assertThat(proxy()).isNotNull();
        proxy().succeed();
    }
}
