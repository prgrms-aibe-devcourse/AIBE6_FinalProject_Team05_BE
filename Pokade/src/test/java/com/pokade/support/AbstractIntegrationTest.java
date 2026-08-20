package com.pokade.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
        // @DataJpaTest 슬라이스는 FlywayAutoConfiguration을 로드하지 않아(스프링 빈으로 기동 시
        // entityManagerFactory와 순환 의존이 생기는 문제도 있음) Flyway를 Spring DI 밖에서
        // 직접 실행한다. 컨테이너가 매 테스트 클래스마다 새로 뜨므로 baseline 없이 V1부터 그대로 적용된다.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }
}
