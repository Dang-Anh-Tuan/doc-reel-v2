-- DocReel MVP schema (SPEC mục 9). Bảng bản 2 (app_setting, video, ...) ở migration riêng.

CREATE TABLE video_job (
    id                   UUID         PRIMARY KEY,
    topic_id             VARCHAR(32)  NOT NULL,
    script_version       INTEGER      NOT NULL,
    content_hash         VARCHAR(64)  NOT NULL,
    idempotency_key      VARCHAR(160) NOT NULL,
    video_type           VARCHAR(20)  NOT NULL,
    state                VARCHAR(20)  NOT NULL,
    last_successful_step VARCHAR(40),
    error_message        TEXT,
    youtube_id           VARCHAR(32),
    tts_provider         VARCHAR(20),
    tts_settings         JSONB        NOT NULL,
    source_channel       VARCHAR(20)  NOT NULL,
    source_ref           VARCHAR(200),
    script               JSONB        NOT NULL,
    version              INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_video_job_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_video_job_state
        CHECK (state IN ('RECEIVED', 'RENDERED', 'VOICED', 'ASSEMBLED', 'UPLOADED', 'FAILED')),
    CONSTRAINT ck_video_job_script_version CHECK (script_version >= 1)
);

-- Worker claim job theo state (FOR UPDATE SKIP LOCKED) và trang danh sách lọc theo state
CREATE INDEX ix_video_job_state_created_at ON video_job (state, created_at);
CREATE INDEX ix_video_job_topic_id ON video_job (topic_id);

CREATE TABLE job_attempt (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id      UUID         NOT NULL REFERENCES video_job (id) ON DELETE CASCADE,
    step        VARCHAR(40)  NOT NULL,
    attempt_no  INTEGER      NOT NULL,
    outcome     VARCHAR(20)  NOT NULL,
    duration_ms INTEGER,
    error       TEXT,
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_attempt_attempt_no CHECK (attempt_no >= 1)
);

CREATE INDEX ix_job_attempt_job_id ON job_attempt (job_id, started_at);

CREATE TABLE tts_usage (
    provider VARCHAR(20) NOT NULL,
    month    VARCHAR(7)  NOT NULL, -- yyyy-MM
    chars    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tts_usage PRIMARY KEY (provider, month),
    CONSTRAINT ck_tts_usage_chars CHECK (chars >= 0)
);
