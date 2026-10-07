package com.skillgapmanager.model;

import java.util.List;

public record AnalysisResponse(
        String job,
        List<String> coveredSkills,
        List<String> missingSkills,
        int readinessPercent) {
}
