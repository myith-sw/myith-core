package com.myith.core.application.auth;

import com.myith.core.application.port.*;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final StarRecordRepository starRecordRepository;
    private final DiagnosisRepository diagnosisRepository;

    public UserService(UserRepository userRepository,
                       RoadmapRepository roadmapRepository,
                       CharacterRepository characterRepository,
                       QuestRepository questRepository,
                       StarRecordRepository starRecordRepository,
                       DiagnosisRepository diagnosisRepository) {
        this.userRepository = userRepository;
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.starRecordRepository = starRecordRepository;
        this.diagnosisRepository = diagnosisRepository;
    }

    @Transactional(readOnly = true)
    public User getMe(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User updateMe(Long userId, String nickname, String profileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.updateProfile(nickname, profileImageUrl);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 1. users 익명화 (D-11)
        user.anonymize();
        userRepository.save(user);

        // 2. 로드맵 ID 수집 (soft delete 전에)
        List<Long> roadmapIds = roadmapRepository.findByUserId(userId).stream()
                .map(Roadmap::getId)
                .toList();

        // 3. user_diagnosis 물리 삭제 (deleted_at 컬럼 없음)
        diagnosisRepository.deleteByRoadmapIds(roadmapIds);

        // 4. quest soft delete
        questRepository.softDeleteByRoadmapIds(roadmapIds);

        // 5. star_record soft delete
        starRecordRepository.softDeleteByUserId(userId);

        // 6. character soft delete
        characterRepository.softDeleteByUserId(userId);

        // 7. roadmap soft delete
        roadmapRepository.softDeleteByUserId(userId);
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long userId) {
            super("User not found: " + userId);
        }
    }
}
