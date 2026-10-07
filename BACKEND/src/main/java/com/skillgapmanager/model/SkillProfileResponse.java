package com.skillgapmanager.model;

import java.util.List;
import java.util.Map;

public record SkillProfileResponse(
        String name,
        String education,
        String branch,
        String year,
        String experience,
        String job,
        Map<String, List<String>> skillsByJob) {
}
