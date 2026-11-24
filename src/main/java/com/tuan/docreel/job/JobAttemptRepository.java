package com.tuan.docreel.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, Long> {

    List<JobAttempt> findByJobIdOrderByStartedAt(UUID jobId);
}
