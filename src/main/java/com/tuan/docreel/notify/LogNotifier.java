package com.tuan.docreel.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LogNotifier implements JobNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStateChanged(JobStateChangedEvent event) {
        MDC.put("jobId", event.jobId().toString());
        try {
            if (event.error() != null) {
                log.warn(
                        "Job {} ({}): {} -> {}: {}",
                        event.jobId(),
                        event.topicId(),
                        event.from(),
                        event.to(),
                        event.error());
            } else {
                log.info("Job {} ({}): {} -> {}", event.jobId(), event.topicId(), event.from(), event.to());
            }
        } finally {
            MDC.remove("jobId");
        }
    }
}
