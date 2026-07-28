package com.myith.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 직후 Worker 소유 공유 테이블 6개의 존재를 확인한다.
 * ddl-auto=validate 가 이미 부재 시 기동을 막지만,
 * Hibernate 스택트레이스만으로는 원인을 파악하기 어렵다.
 * 이 러너는 사람이 읽을 수 있는 안내 메시지를 남긴다.
 */
@Component
public class SharedSchemaCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SharedSchemaCheckRunner.class);

    private static final List<String> SHARED_TABLES = List.of(
            "job", "job_profile", "ncs_unit", "ncs_certification",
            "skill_ncs_map", "user_competency"
    );

    private final JdbcTemplate jdbcTemplate;

    public SharedSchemaCheckRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();

        for (String table : SHARED_TABLES) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT to_regclass('public." + table + "') IS NOT NULL", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                missing.add(table);
            }
        }

        if (missing.isEmpty()) {
            log.info("공유 테이블 점검 완료: Worker 소유 테이블 {}개 모두 존재합니다.", SHARED_TABLES.size());
        } else {
            for (String table : missing) {
                log.error("공유 테이블 {} 이(가) 없습니다. myith-worker 에서 'alembic upgrade head' 를 먼저 실행하세요.", table);
            }
        }
    }
}
