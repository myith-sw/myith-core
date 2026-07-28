package com.myith.core.config;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

import java.util.Set;

/**
 * Worker 소유 테이블을 Hibernate DDL 대상에서 제외한다.
 *
 * ddl-auto: update 는 없는 테이블을 만든다. Core 가 Worker 마이그레이션보다 먼저 뜨면
 * Worker 소유 테이블을 Core 엔티티 모양대로 생성해버리고, 그 뒤 Worker Alembic 이
 * "이미 존재"로 실패한다. 이 필터가 그 경로를 차단한다.
 *
 * 읽기는 정상 동작한다 — 필터는 DDL 생성에만 적용되고 조회에는 영향이 없다.
 * 테이블이 실제로 없으면 조회 시점에 에러가 나는데, 그게 조용한 스키마 오염보다 낫다.
 */
public class WorkerOwnedTableFilter implements SchemaFilterProvider, SchemaFilter {

    private static final Set<String> WORKER_OWNED = Set.of(
            "job", "job_profile", "ncs_unit", "ncs_certification",
            "skill_ncs_map", "user_competency", "user_quest_guidance");

    @Override public SchemaFilter getCreateFilter()    { return this; }
    @Override public SchemaFilter getDropFilter()      { return this; }
    @Override public SchemaFilter getMigrateFilter()    { return this; }
    @Override public SchemaFilter getValidateFilter()   { return this; }
    @Override public SchemaFilter getTruncatorFilter()  { return this; }

    @Override
    public boolean includeTable(Table table) {
        return !WORKER_OWNED.contains(table.getName().toLowerCase());
    }

    @Override public boolean includeNamespace(Namespace namespace) { return true; }
    @Override public boolean includeSequence(Sequence sequence)    { return true; }
}
