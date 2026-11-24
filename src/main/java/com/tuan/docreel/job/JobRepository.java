package com.tuan.docreel.job;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<VideoJob, UUID> {

    Optional<VideoJob> findByIdempotencyKey(String idempotencyKey);

    /**
     * Claim job kế tiếp cần chạy (cũ nhất trước); dòng đang bị worker khác khóa thì bỏ qua. Phải gọi trong
     * transaction: khóa giữ tới khi commit. {@code blocked} không được rỗng (đang chờ backoff retry).
     */
    @Query(value = """
                    SELECT * FROM video_job
                    WHERE state IN ('RECEIVED', 'RENDERED', 'VOICED', 'ASSEMBLED')
                      AND id NOT IN (:blocked)
                    ORDER BY created_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<VideoJob> claimNext(@Param("blocked") Collection<UUID> blocked);

    Page<VideoJob> findByState(JobState state, Pageable pageable);
}
