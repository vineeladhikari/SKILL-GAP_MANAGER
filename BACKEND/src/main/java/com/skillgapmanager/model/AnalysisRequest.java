package com.skillgapmanager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalysisRequest(
        @NotBlank String job,
        @NotNull List<@NotBlank String> knownSkills) {
}
