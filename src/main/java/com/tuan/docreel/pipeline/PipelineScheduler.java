package com.tuan.docreel.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gọi worker mỗi 10s cho tới khi hết việc. Tắt ở profile {@code test} để IT khác không bị worker nhặt job. */
@Component
@Profile("!test")
class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

    private final PipelineWorker worker;

    PipelineScheduler(PipelineWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            initialDelayString = "${docreel.pipeline.poll-interval:PT10S}",
            fixedDelayString = "${docreel.pipeline.poll-interval:PT10S}")
    void poll() {
        try {
            while (worker.runOnce()) {
                // còn việc: chạy tiếp ngay
            }
        } catch (RuntimeException e) {
            log.error("Worker lỗi ngoài dự kiến, thử lại ở lần sau", e);
        }
    }
}
