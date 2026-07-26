package com.myith.core.application.port;

import com.myith.core.domain.star.StarRecord;

import java.util.List;
import java.util.Optional;

public interface StarRecordRepository {
    StarRecord save(StarRecord record);
    Optional<StarRecord> findByQuestId(Long questId);
    List<StarRecord> findByCursor(Long userId, Long cursor, String completeness, int size);
    void softDeleteByUserId(Long userId);
}
