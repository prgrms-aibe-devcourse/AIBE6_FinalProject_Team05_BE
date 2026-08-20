package com.pokade.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Spring Boot 4.0.7의 FlywayAutoConfiguration이 HibernateJpaConfiguration과
 * "Circular depends-on relationship between 'flyway' and 'entityManagerFactory'"를 일으켜
 * (spring.flyway.enabled=false로 그 자동설정 자체를 꺼둠), 환경이 준비되는 시점에
 * Flyway를 빈 등록 없이 직접 실행한다 — entityManagerFactory 빈이 만들어지기(=검증되기)
 * 전에 항상 끝나 있음을 보장한다.
 */
public class FlywayMigrationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        String url = env.getProperty("spring.datasource.url");
        if (url == null) {
            return;
        }
        String user = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");
        boolean baselineOnMigrate = env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false);
        String baselineVersion = env.getProperty("spring.flyway.baseline-version", "1");

        Flyway.configure()
                .dataSource(url, user, password)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load()
                .migrate();
    }
}
