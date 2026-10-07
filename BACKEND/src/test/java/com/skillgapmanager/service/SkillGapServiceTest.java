package com.skillgapmanager.service;

import com.skillgapmanager.model.AnalysisRequest;
import com.skillgapmanager.model.AnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillGapServiceTest {
    private final SkillGapService service = new SkillGapService();

    @Test
    void returnsCoveredAndMissingSkillsInRecommendedOrder() {
        AnalysisResponse result = service.analyze(new AnalysisRequest(
                "Java Developer",
                List.of("Spring Boot", "Core Java & OOP")));

        assertEquals(List.of("Core Java & OOP", "Spring Boot"), result.coveredSkills());
        assertEquals(12, result.missingSkills().size());
        assertEquals(14, result.readinessPercent());
    }

    @Test
    void returnsFullReadinessWhenAllSkillsAreKnown() {
        List<String> skills = service.getRoles().get("Data Analyst");
        AnalysisResponse result = service.analyze(new AnalysisRequest("Data Analyst", skills));

        assertEquals(100, result.readinessPercent());
        assertEquals(List.of(), result.missingSkills());
    }

    @Test
    void rejectsUnknownRoles() {
        assertThrows(ResponseStatusException.class,
                () -> service.analyze(new AnalysisRequest("Astronaut", List.of())));
    }
}
