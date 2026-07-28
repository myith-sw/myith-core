# 시드는 Core 가 소유하지 않는다

job · job_profile · ncs_unit · ncs_certification · skill_ncs_map · user_competency 는
Worker 소유 테이블이다(CLAUDE.md C-3). 시드 적재도 Worker 가 한다.

    cd myith-worker
    alembic upgrade head
    python -m app.seed.load_all

Core 의 Flyway 에 `classpath:db/seed` 를 추가하지 마라.
Worker 실데이터와 PK 가 충돌해 마이그레이션이 실패한다.
