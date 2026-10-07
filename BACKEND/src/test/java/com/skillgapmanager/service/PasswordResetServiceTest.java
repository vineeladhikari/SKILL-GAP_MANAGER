package com.skillgapmanager.service;

import com.skillgapmanager.model.Account;
import com.skillgapmanager.model.PasswordResetToken;
import com.skillgapmanager.repository.AccountRepository;
import com.skillgapmanager.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {
    private static final Instant NOW = Instant.parse("2026-04-05T12:00:00Z");
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SmtpEmailService emailService = mock(SmtpEmailService.class);
    private final FindByIndexNameSessionRepository<Session> sessions = mock(FindByIndexNameSessionRepository.class);
    private final PasswordResetService service = new PasswordResetService(
            accounts, resetTokens, passwordEncoder, emailService, sessions,
            Clock.fixed(NOW, ZoneOffset.UTC), new java.security.SecureRandom());

    @BeforeEach
    void configureEmail() {
        when(emailService.isConfigured()).thenReturn(true);
    }

    @Test
    void resetRequestUsesNormalizedAddressAndPersistsOnlyTokenHash() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(12L);
        when(account.getEmail()).thenReturn("learner@example.com");
        when(accounts.findByEmail("learner@example.com")).thenReturn(Optional.of(account));
        when(resetTokens.countByAccountIdAndCreatedAtAfter(eq(12L), any())).thenReturn(0L);

        service.requestReset("Learner@Example.com");

        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(tokenCaptor.capture());
        PasswordResetToken stored = tokenCaptor.getValue();
        assertEquals(NOW, stored.getCreatedAt());
        assertEquals(NOW.plusSeconds(1200), stored.getExpiresAt());
        assertEquals(64, stored.getTokenHash().length());
        verify(emailService).sendPasswordReset(eq("learner@example.com"), any());
    }

    @Test
    void resetChangesPasswordDeletesTokensAndInvalidatesAllSessions() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(12L);
        when(account.getEmail()).thenReturn("learner@example.com");
        PasswordResetToken token = new PasswordResetToken(12L, hash("valid-token"), NOW,
                NOW.plusSeconds(1200));
        when(resetTokens.findByTokenHashForUpdate(hash("valid-token"))).thenReturn(Optional.of(token));
        when(accounts.findById(12L)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-secure-password")).thenReturn("bcrypt-hash");
        Session firstSession = mock(Session.class);
        Session secondSession = mock(Session.class);
        when(firstSession.getId()).thenReturn("session-1");
        when(secondSession.getId()).thenReturn("session-2");
        when(sessions.findByPrincipalName("learner@example.com"))
                .thenReturn(Map.of("session-1", firstSession, "session-2", secondSession));

        service.resetPassword("valid-token", "new-secure-password");

        verify(account).updatePasswordHash("bcrypt-hash");
        verify(resetTokens).deleteAllByAccountId(12L);
        verify(sessions).deleteById("session-1");
        verify(sessions).deleteById("session-2");
    }

    @Test
    void expiredOrUnknownTokenCannotChangePassword() {
        PasswordResetToken expired = new PasswordResetToken(12L, hash("expired-token"),
                NOW.minusSeconds(1201), NOW.minusSeconds(1));
        when(resetTokens.findByTokenHashForUpdate(hash("expired-token"))).thenReturn(Optional.of(expired));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("expired-token", "new-secure-password"));

        assertEquals(400, failure.getStatusCode().value());
        verify(accounts, never()).findById(any());
    }

    @Test
    void requestLimitDoesNotSendMoreThanThreeEmailsPerHour() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(12L);
        when(accounts.findByEmail("learner@example.com")).thenReturn(Optional.of(account));
        when(resetTokens.countByAccountIdAndCreatedAtAfter(eq(12L), any())).thenReturn(3L);

        service.requestReset("learner@example.com");

        verify(emailService, never()).sendPasswordReset(any(), any());
        verify(resetTokens, never()).save(any());
    }

    @Test
    void resetRejectsPasswordBeyondBcryptByteLimit() {
        String tooLong = "é".repeat(37);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("unused-token", tooLong));

        assertEquals(400, failure.getStatusCode().value());
        verify(resetTokens, never()).findByTokenHashForUpdate(any());
    }

    @Test
    void resetRequestCreatesSixDigitOtpAndStoresItsHash() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(12L);
        when(account.getEmail()).thenReturn("learner@example.com");
        when(accounts.findByEmail("learner@example.com")).thenReturn(Optional.of(account));
        when(resetTokens.countByAccountIdAndCreatedAtAfter(eq(12L), any())).thenReturn(0L);
        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);

        service.requestReset("learner@example.com");
        verify(resetTokens).save(tokenCaptor.capture());
        var otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("learner@example.com"), otpCaptor.capture());
        String otp = otpCaptor.getValue();

        assertEquals(6, otp.length());
        assertTrue(otp.chars().allMatch(Character::isDigit));
        assertFalse(otp.equals(tokenCaptor.getValue().getTokenHash()));
        assertEquals(hash(otp), tokenCaptor.getValue().getTokenHash());
    }

    private static String hash(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
