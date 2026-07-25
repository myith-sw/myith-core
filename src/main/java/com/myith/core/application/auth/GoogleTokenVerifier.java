package com.myith.core.application.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        log.info("Google OAuth client-id configured: {}...{}",
                clientId.length() > 8 ? clientId.substring(0, 8) : clientId,
                clientId.length() > 8 ? clientId.substring(clientId.length() - 4) : "");
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUserInfo verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new InvalidGoogleTokenException("Invalid Google ID token");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUserInfo(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (InvalidGoogleTokenException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Google verification infrastructure error", e);
            throw new GoogleVerificationException("Google token verification failed", e);
        } catch (Exception e) {
            log.warn("Malformed Google ID token", e);
            throw new InvalidGoogleTokenException("Invalid Google ID token");
        }
    }

    public record GoogleUserInfo(String googleId, String email, String name, String pictureUrl) {}

    public static class InvalidGoogleTokenException extends RuntimeException {
        public InvalidGoogleTokenException(String message) { super(message); }
    }

    public static class GoogleVerificationException extends RuntimeException {
        public GoogleVerificationException(String message, Throwable cause) { super(message, cause); }
    }
}
