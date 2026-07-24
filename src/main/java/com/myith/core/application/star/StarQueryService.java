package com.myith.core.application.star;

import com.myith.core.application.port.StarRecordRepository;
import com.myith.core.domain.star.StarRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StarQueryService {

    private final StarRecordRepository starRecordRepository;

    public StarQueryService(StarRecordRepository starRecordRepository) {
        this.starRecordRepository = starRecordRepository;
    }

    public CursorResult getRecords(Long userId, Long cursor, int size, String completeness) {
        // size+1 조회해서 다음 페이지 존재 여부 판단
        List<StarRecord> records = starRecordRepository.findByCursor(userId, cursor, completeness, size + 1);

        boolean hasNext = records.size() > size;
        List<StarRecord> page = hasNext ? records.subList(0, size) : records;

        Long nextCursor = hasNext ? page.getLast().getId() : null;

        List<StarRecordDto> dtos = page.stream()
                .map(r -> new StarRecordDto(r.getId(), r.getQuestId(), r.getSituation(),
                        r.getTask(), r.getAction(), r.getResult(),
                        r.getCompleteness(), r.getTags()))
                .toList();

        return new CursorResult(dtos, nextCursor);
    }

    public record CursorResult(List<StarRecordDto> records, Long nextCursor) {}
    public record StarRecordDto(Long id, Long questId, String situation, String task,
                                String action, String result, String completeness,
                                List<String> tags) {}
}
