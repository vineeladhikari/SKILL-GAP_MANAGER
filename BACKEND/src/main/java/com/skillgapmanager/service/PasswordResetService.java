package com.skillgapmanager.service;

import com.skillgapmanager.model.Account;
import com.skillgapmanager.model.PasswordResetToken;
import com.skillgapmanager.repository.AccountRepository;
import com.skillgapmanager.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

@Service
public class PasswordResetService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(20);
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final SmtpEmailService emailService;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public PasswordResetService(AccountRepository accounts,
                                PasswordResetTokenRepository resetTokens,
                                PasswordEncoder passwordEncoder,
                                SmtpEmailService emailService,
                                FindByIndexNameSessionRepository<? extends Session> sessions) {
        this(accounts, resetTokens, passwordEncoder, emailService, sessions, Clock.systemUTC(), new SecureRandom());
    }

    PasswordResetService(AccountRepository accounts,
                         PasswordResetTokenRepository resetTokens,
                         PasswordEncoder passwordEncoder,
                         SmtpEmailService emailService,
                         FindByIndexNameSessionRepository<? extends Session> sessions,
                         Clock clock,
                         SecureRandom secureRandom) {
        this.accounts = accounts;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessions = sessions;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public String requestReset(String email) {
        String normalizedEmail = AccountService.normalizeEmail(email);
        var accountOpt = accounts.findByEmail(normalizedEmail);
        if (accountOpt.isEmpty()) {
            return null;
        }

        var account = accountOpt.get();
        Instant now = clock.instant();
        if (resetTokens.countByAccountIdAndCreatedAtAfter(account.getId(), now.minus(REQUEST_WINDOW))
                >= MAX_REQUESTS_PER_HOUR) {
            return null;
        }

        String otp = createOtp();
        resetTokens.save(new PasswordResetToken(account.getId(), hashToken(otp), now,
                now.plus(TOKEN_LIFETIME)));

        if (emailService.isConfigured()) {
            emailService.sendPasswordReset(account.getEmail(), otp);
            return null;
        } else {
            return otp;
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be no more than 72 UTF-8 bytes");
        }

        Instant now = clock.instant();
        PasswordResetToken resetToken = resetTokens.findByTokenHashForUpdate(hashToken(token))
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(PasswordResetService::invalidToken);
        Long accountId = Objects.requireNonNull(resetToken.getAccountId(),
                "Persisted password-reset token has no account ID");
        Account account = accounts.findById(accountId)
                .orElseThrow(PasswordResetService::invalidToken);

        account.updatePasswordHash(passwordEncoder.encode(newPassword));
        resetTokens.deleteAllByAccountId(accountId);
        String email = Objects.requireNonNull(account.getEmail());
        Map<String, ? extends Session> activeSessions = sessions.findByPrincipalName(email);
        for (Session session : activeSessions.values()) {
            sessions.deleteById(session.getId());
        }
    }

    private String createOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This verification code is invalid or has expired. Request a new OTP.");
    }
}
