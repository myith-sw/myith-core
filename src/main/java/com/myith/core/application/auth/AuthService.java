package com.myith.core.application.auth;

import com.myith.core.application.port.UserRepository;
import com.myith.core.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public AuthService(GoogleTokenVerifier googleTokenVerifier,
                       JwtProvider jwtProvider,
                       UserRepository userRepository) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResult loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(idToken);

        boolean isNewUser = false;
        User user = userRepository.findByGoogleId(info.googleId()).orElse(null);

        if (user == null) {
            user = User.createFromGoogle(info.email(), info.googleId(), info.name(), info.pictureUrl());
            user = userRepository.save(user);
            isNewUser = true;
        }

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return new AuthResult(accessToken, refreshToken, isNewUser);
    }

    public TokenResult refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        if (!"refresh".equals(jwtProvider.getTokenType(refreshToken))) {
            throw new InvalidTokenException("Not a refresh token");
        }

        Long userId = jwtProvider.getUserIdFromToken(refreshToken);
        userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        String accessToken = jwtProvider.createAccessToken(userId);
        return new TokenResult(accessToken);
    }

    public record AuthResult(String accessToken, String refreshToken, boolean isNewUser) {}
    public record TokenResult(String accessToken) {}

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) { super(message); }
    }
}
