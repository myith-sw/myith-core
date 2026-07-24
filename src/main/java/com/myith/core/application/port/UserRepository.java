package com.myith.core.application.port;

import com.myith.core.domain.user.User;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);
}
