-- =============================================
-- Core 소유 테이블 (쓰기)
-- =============================================

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    google_id           VARCHAR(255) UNIQUE,
    nickname            VARCHAR(50)  NOT NULL,
    profile_image_url   VARCHAR(512),
    last_heartbeat_at   TIMESTAMPTZ,
    last_active_at      TIMESTAMPTZ,
    last_nudge_sent_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE roadmap (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id),
    job_code          VARCHAR(50)  NOT NULL,
    profile_version   INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    generation_state  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count       INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    archived_at       TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_roadmap_user_id ON roadmap(user_id);
CREATE INDEX idx_roadmap_user_status ON roadmap(user_id, status);

CREATE TABLE character (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    roadmap_id  BIGINT       NOT NULL UNIQUE REFERENCES roadmap(id),
    species     VARCHAR(50)  NOT NULL,
    nickname    VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_character_user_id ON character(user_id);

CREATE TABLE quest (
    id                  BIGSERIAL    PRIMARY KEY,
    roadmap_id          BIGINT       NOT NULL REFERENCES roadmap(id),
    skill_code          VARCHAR(50),
    axis_code           VARCHAR(50)  NOT NULL,
    level               INT          NOT NULL,
    order_in_level      INT          NOT NULL,
    title               VARCHAR(255) NOT NULL,
    completion_criteria TEXT,
    ncs_unit_code       VARCHAR(50),
    source              VARCHAR(20)  NOT NULL DEFAULT 'SKILL',
    status              VARCHAR(20)  NOT NULL DEFAULT 'LOCKED',
    completed_at        TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_quest_roadmap_id ON quest(roadmap_id);
CREATE INDEX idx_quest_roadmap_level ON quest(roadmap_id, level, order_in_level);

CREATE TABLE user_diagnosis (
    id          BIGSERIAL      PRIMARY KEY,
    roadmap_id  BIGINT         NOT NULL REFERENCES roadmap(id),
    skill_code  VARCHAR(50)    NOT NULL,
    mastery     NUMERIC(3,2)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (roadmap_id, skill_code)
);

CREATE TABLE star_record (
    id            BIGSERIAL    PRIMARY KEY,
    quest_id      BIGINT       NOT NULL REFERENCES quest(id),
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    situation     TEXT,
    task          TEXT,
    action        TEXT,
    result        TEXT,
    completeness  VARCHAR(20),
    tags          VARCHAR[],
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX idx_star_record_user ON star_record(user_id);
CREATE INDEX idx_star_record_quest ON star_record(quest_id);
CREATE INDEX idx_star_record_filter ON star_record(user_id, completeness);

CREATE TABLE dashboard_snapshot (
    roadmap_id      BIGINT       PRIMARY KEY REFERENCES roadmap(id),
    completion_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    stage           VARCHAR(20)  NOT NULL DEFAULT '시작',
    max_stage       VARCHAR(20)  NOT NULL DEFAULT '시작',
    radar           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    computed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_id        UUID         NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox(status) WHERE status = 'PENDING';

CREATE TABLE processed_event (
    event_id    UUID        PRIMARY KEY,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 읽기 전용 테이블은 Worker가 소유한다.
-- Worker 배포 시 별도 마이그레이션으로 생성된다.
-- Core는 job, job_profile, user_competency, ncs_unit, ncs_certification을 읽기만 한다.
