package com.skillgapmanager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SkillProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String education,
        @NotBlank @Size(max = 80) String branch,
        @NotBlank @Size(max = 40) String year,
        @NotBlank @Size(max = 40) String experience,
        @NotBlank @Size(max = 80) String job,
        @NotNull Map<String, List<@NotBlank @Size(max = 120) String>> skillsByJob) {
}
