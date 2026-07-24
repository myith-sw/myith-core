package com.myith.core.domain.roadmap;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 자가진단(user_diagnosis)과 AI 보정(user_competency)을 병합한다.
 *
 * 규칙 (D-2):
 *   user_competency에 해당 스킬이 있고 evidence가 비어있지 않으면 → 그 값
 *   아니면 → user_diagnosis의 값
 *   둘 다 없으면 → 0
 */
public class MasteryMerger {

    /**
     * @param selfAssessment  자가진단 결과 (skillCode → mastery)
     * @param aiAssessment    AI 보정 결과 (skillCode → CompetencyEntry). null이면 보정 없음
     * @param skillCode       대상 스킬
     * @return 최종 보유도 M (0.00 ~ 1.00)
     */
    public static BigDecimal merge(Map<String, BigDecimal> selfAssessment,
                                   Map<String, CompetencyEntry> aiAssessment,
                                   String skillCode) {
        if (aiAssessment != null) {
            CompetencyEntry ai = aiAssessment.get(skillCode);
            if (ai != null && ai.evidence() != null && !ai.evidence().isBlank()) {
                return ai.mastery();
            }
        }

        BigDecimal selfValue = selfAssessment.get(skillCode);
        if (selfValue != null) {
            return selfValue;
        }

        return BigDecimal.ZERO;
    }

    public record CompetencyEntry(BigDecimal mastery, String evidence) {}
}
