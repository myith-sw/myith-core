-- 시드 데이터: Worker 없이 Core 전 기능 개발용

INSERT INTO job (job_code, job_name, category_code, category_name, tagline) VALUES
('backend', '백엔드 개발자', 'dev', '개발', '서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.');

INSERT INTO job_profile (job_code, version, axes, skills, levels, prerequisites, questions, quest_templates, activity_quests) VALUES
('backend', 1,
'[
  {"axisCode":"programming","axisName":"프로그래밍기초"},
  {"axisCode":"cs","axisName":"CS·자료구조"},
  {"axisCode":"data-io","axisName":"데이터입출력"},
  {"axisCode":"server-api","axisName":"서버·API"},
  {"axisCode":"collab","axisName":"협업·형상관리"},
  {"axisCode":"devops","axisName":"배포·운영"}
]'::jsonb,
'[
  {"skillCode":"git","axisCode":"collab","skillName":"Git","difficulty":0.10,"prevalence":0.95},
  {"skillCode":"java","axisCode":"programming","skillName":"Java","difficulty":0.22,"prevalence":0.84},
  {"skillCode":"sql","axisCode":"data-io","skillName":"SQL","difficulty":0.31,"prevalence":0.82},
  {"skillCode":"spring","axisCode":"server-api","skillName":"Spring","difficulty":0.34,"prevalence":0.79},
  {"skillCode":"rest","axisCode":"server-api","skillName":"REST","difficulty":0.44,"prevalence":0.63},
  {"skillCode":"jpa","axisCode":"data-io","skillName":"JPA","difficulty":0.50,"prevalence":0.53},
  {"skillCode":"redis","axisCode":"data-io","skillName":"Redis","difficulty":0.63,"prevalence":0.32},
  {"skillCode":"docker","axisCode":"devops","skillName":"Docker","difficulty":0.62,"prevalence":0.47},
  {"skillCode":"aws","axisCode":"devops","skillName":"AWS","difficulty":0.61,"prevalence":0.58},
  {"skillCode":"kafka","axisCode":"devops","skillName":"Kafka","difficulty":0.78,"prevalence":0.26}
]'::jsonb,
'[
  {"level":1,"skills":["git","java"]},
  {"level":2,"skills":["sql","spring"]},
  {"level":3,"skills":["rest","jpa"]},
  {"level":4,"skills":["docker","redis"]},
  {"level":5,"skills":["aws","kafka"]}
]'::jsonb,
'[
  {"from":"java","to":"spring"},
  {"from":"sql","to":"jpa"},
  {"from":"spring","to":"rest"}
]'::jsonb,
'[
  {"skillCode":"git","axisCode":"collab","text":"Git을 사용하여 브랜치 전략을 세우고 협업할 수 있다."},
  {"skillCode":"java","axisCode":"programming","text":"Java로 객체지향 설계 원칙에 따라 프로그램을 작성할 수 있다."},
  {"skillCode":"sql","axisCode":"data-io","text":"SQL을 사용하여 데이터를 조회하고 조작할 수 있다."},
  {"skillCode":"spring","axisCode":"server-api","text":"Spring Framework의 핵심 개념을 이해하고 활용할 수 있다."},
  {"skillCode":"rest","axisCode":"server-api","text":"RESTful API를 설계하고 구현할 수 있다."},
  {"skillCode":"jpa","axisCode":"data-io","text":"JPA를 활용하여 객체-관계 매핑을 구현할 수 있다."},
  {"skillCode":"docker","axisCode":"devops","text":"Docker를 사용하여 애플리케이션을 컨테이너화할 수 있다."},
  {"skillCode":"aws","axisCode":"devops","text":"AWS 핵심 서비스를 활용하여 서비스를 배포할 수 있다."}
]'::jsonb,
'[
  {"skillCode":"git","title":"Git 브랜치 전략 수립","completionCriteria":"Git Flow 또는 GitHub Flow를 적용한 프로젝트를 완성한다.","ncsUnitCode":"L2001010104_18v4"},
  {"skillCode":"java","title":"Java 객체지향 프로그래밍","completionCriteria":"SOLID 원칙을 적용한 Java 프로젝트를 완성한다.","ncsUnitCode":"L2001010106_18v4"},
  {"skillCode":"sql","title":"SQL 데이터 모델링과 쿼리 작성","completionCriteria":"정규화된 스키마를 설계하고 복잡한 조회 쿼리를 작성한다.","ncsUnitCode":"L2001010102_18v4"},
  {"skillCode":"spring","title":"Spring Boot 애플리케이션 개발","completionCriteria":"Spring Boot로 REST API 서버를 구현하고 테스트한다.","ncsUnitCode":"L2001010108_18v4"},
  {"skillCode":"rest","title":"RESTful API 설계와 구현","completionCriteria":"REST 원칙에 맞는 API를 설계하고 문서화한다.","ncsUnitCode":"L2001010108_18v4"},
  {"skillCode":"jpa","title":"JPA 엔티티 매핑과 쿼리 최적화","completionCriteria":"연관관계 매핑과 N+1 문제 해결을 포함한 JPA 프로젝트를 완성한다.","ncsUnitCode":"L2001010102_18v4"},
  {"skillCode":"redis","title":"Redis 캐시 전략 구현","completionCriteria":"캐시 정책을 설계하고 적용한다.","ncsUnitCode":"L2001010102_18v4"},
  {"skillCode":"docker","title":"Docker 컨테이너 환경 구성","completionCriteria":"Dockerfile과 docker-compose로 멀티 컨테이너 환경을 구성한다.","ncsUnitCode":"L2001010110_18v4"},
  {"skillCode":"aws","title":"AWS 클라우드 인프라 구축","completionCriteria":"EC2, RDS, S3를 활용한 서비스 인프라를 구성한다.","ncsUnitCode":"L2001010110_18v4"},
  {"skillCode":"kafka","title":"Kafka 메시지 시스템 구축","completionCriteria":"Kafka Producer/Consumer를 구현하고 메시지 처리를 검증한다.","ncsUnitCode":"L2001010110_18v4"}
]'::jsonb,
'[
  {"axisCode":"cs","level":5,"title":"CS 면접 질문을 정리한다","completionCriteria":"자료구조, 알고리즘, 운영체제, 네트워크 핵심 질문 30개를 정리하고 답변을 작성한다."},
  {"axisCode":"server-api","level":6,"title":"협업 프로젝트로 실전을 쌓는다","completionCriteria":"팀 프로젝트에서 백엔드 역할을 맡아 API 설계부터 배포까지 수행한다."}
]'::jsonb
);

-- NCS 능력단위
INSERT INTO ncs_unit (code, name, description, level) VALUES
('L2001010106_18v4', '프로그래밍 언어 활용', '프로그래밍 언어를 활용하여 기본 응용 소프트웨어를 구현하는 능력', 3),
('L2001010102_18v4', '데이터베이스 구현', 'DBMS를 활용하여 데이터베이스를 구축하고 관리하는 능력', 3),
('L2001010108_18v4', '서버프로그램 구현', '서버 프로그램을 개발하고 테스트하는 능력', 4),
('L2001010104_18v4', '통합 구현', '모듈 간 연계와 통합을 수행하는 능력', 4),
('L2001010109_18v4', 'SW개발 보안 구축', '보안 요구사항을 분석하고 안전한 SW를 개발하는 능력', 5),
('L2001010110_18v4', '애플리케이션 배포·운영', '애플리케이션을 배포하고 운영 환경을 관리하는 능력', 4);

-- NCS 연계 자격
INSERT INTO ncs_certification (ncs_unit_code, cert_name) VALUES
('L2001010102_18v4', 'SQLD'),
('L2001010102_18v4', 'SQLP'),
('L2001010108_18v4', '정보처리기사'),
('L2001010104_18v4', '정보처리기사'),
('L2001010109_18v4', '정보보안기사'),
('L2001010110_18v4', '리눅스마스터 2급');
