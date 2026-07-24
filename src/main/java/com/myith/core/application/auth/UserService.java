package com.myith.core.application.auth;

import com.myith.core.application.port.UserRepository;
import com.myith.core.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        user.anonymize();
        userRepository.save(user);
        // TODO: 연관 데이터(roadmap, character, quest, star_record) deleted_at 세팅은
        //       해당 도메인 리포지토리 구현 후 추가
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long userId) {
            super("User not found: " + userId);
        }
    }
}
