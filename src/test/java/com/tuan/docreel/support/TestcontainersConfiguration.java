package com.tuan.docreel.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres 16 cho integration test. Spring cache context giữa các test cùng cấu hình → dùng chung container;
 * {@code withReuse(true)} giữ container giữa các lần chạy nếu bật {@code testcontainers.reuse.enable=true} trong
 * {@code ~/.testcontainers.properties}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE).withReuse(true);
    }
}
