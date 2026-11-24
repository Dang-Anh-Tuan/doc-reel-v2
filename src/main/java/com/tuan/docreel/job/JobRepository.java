package com.tuan.docreel.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<VideoJob, UUID> {

    Optional<VideoJob> findByIdempotencyKey(String idempotencyKey);

    List<VideoJob> findByState(JobState state, Pageable pageable);
}
