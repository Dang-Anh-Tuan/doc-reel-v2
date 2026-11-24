package com.tuan.docreel.pipeline;

import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PipelineConfig {

    /** {@code docreel.pipeline.retry-backoff}: danh sách ISO-8601 cách nhau dấu phẩy; mặc định PT30S,PT2M. */
    @Bean
    RetryPolicy retryPolicy(@Value("${docreel.pipeline.retry-backoff:PT30S,PT2M}") String backoff) {
        return new RetryPolicy(Arrays.stream(backoff.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Duration::parse)
                .toList());
    }
}
