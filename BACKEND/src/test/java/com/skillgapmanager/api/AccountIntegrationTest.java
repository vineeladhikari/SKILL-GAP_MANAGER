package com.skillgapmanager.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillgapmanager.repository.AccountRepository;
import com.skillgapmanager.repository.PasswordResetTokenRepository;
import com.skillgapmanager.repository.SkillProfileRepository;
import com.skillgapmanager.service.SmtpEmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:skillgap-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountIntegrationTest {
    private static final String PASSWORD = "a-strong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private SkillProfileRepository profiles;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private SmtpEmailService emailService;

    @BeforeEach
    void clearAccounts() {
        resetTokens.deleteAll();
        profiles.deleteAll();
        accounts.deleteAll();
        reset(emailService);
        when(emailService.isConfigured()).thenReturn(true);
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "Learner@Example.com",
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("learner@example.com")));

        var account = accounts.findByEmail("learner@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, account.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, account.getPasswordHash()));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "learner@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationAcceptsFourCharacterPasswordAndRejectsShorterPasswords() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "short-password@example.com",
                                "password", "abc"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "four-character@example.com",
                                "password", "abcd"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "four-character@example.com",
                                "password", "abcd"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanSaveAndReloadProfileAndSkills() throws Exception {
        Cookie sessionCookie = registerAndLogin("learner@example.com");
        Map<String, Object> profile = Map.of(
                "name", "Asha Patel",
                "education", "B.Tech",
                "branch", "CSE",
                "year", "3rd Year",
                "experience", "Fresher",
                "job", "Java Developer",
                "skillsByJob", Map.of("Java Developer", List.of("Core Java & OOP", "Spring Boot")));

        mockMvc.perform(put("/api/account/profile")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Asha Patel")));

        Map<String, Object> updatedProfile = new java.util.HashMap<>(profile);
        updatedProfile.put("name", "Asha Patel Updated");
        updatedProfile.put("skillsByJob", Map.of("Java Developer", List.of("Core Java & OOP")));
        mockMvc.perform(put("/api/account/profile")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updatedProfile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Asha Patel Updated")));

        mockMvc.perform(get("/api/account/profile").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job", is("Java Developer")))
                .andExpect(jsonPath("$.skillsByJob['Java Developer'][0]", is("Core Java & OOP")))
                .andExpect(jsonPath("$.skillsByJob['Java Developer'].length()", is(1)));

        mockMvc.perform(delete("/api/account/profile")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/account/profile").cookie(sessionCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void profileIsUnavailableWithoutAnAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profilesAreIsolatedBetweenAccounts() throws Exception {
        Cookie firstAccount = registerAndLogin("first@example.com");
        mockMvc.perform(put("/api/account/profile")
                        .cookie(firstAccount)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "First User",
                                "education", "B.Tech",
                                "branch", "CSE",
                                "year", "3rd Year",
                                "experience", "Fresher",
                                "job", "Java Developer",
                                "skillsByJob", Map.of("Java Developer", List.of("Spring Boot"))))))
                .andExpect(status().isOk());

        Cookie secondAccount = registerAndLogin("second@example.com");
        mockMvc.perform(get("/api/account/profile").cookie(secondAccount))
                .andExpect(status().isNoContent());
    }

    @Test
    void registrationRejectsRequestsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "learner@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordResetUsesGenericResponseAndInvalidatesExistingSession() throws Exception {
        Cookie existingSession = registerAndLogin("reset@example.com");
        String forgotRequest = objectMapper.writeValueAsString(Map.of("email", "reset@example.com"));
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content(forgotRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message",
                        is("If an account exists for this email, a one-time verification code will be sent.")));

        var otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("reset@example.com"), otpCaptor.capture());
        String token = otpCaptor.getValue();
        Assertions.assertNotNull(token);
        Assertions.assertEquals(6, token.length());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message",
                        is("If an account exists for this email, a one-time verification code will be sent.")));
        verify(emailService, org.mockito.Mockito.times(1)).sendPasswordReset(any(), any());

        String newPassword = "abcd";
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "password", newPassword))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").cookie(existingSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "reset@example.com",
                                "password", newPassword))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "reset@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "password", newPassword))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicEndpointsRolesAndAnalyzeAreAccessible() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['Java Developer']").isArray());

        mockMvc.perform(post("/api/analyze")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "job", "Java Developer",
                                "knownSkills", List.of("Core Java & OOP", "Spring Boot")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job", is("Java Developer")))
                .andExpect(jsonPath("$.coveredSkills.length()", is(2)))
                .andExpect(jsonPath("$.missingSkills.length()", is(12)));
    }

    private Cookie registerAndLogin(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(credentials))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("SESSION");
    }
}
