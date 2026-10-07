package com.skillgapmanager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillgapmanager.model.SkillProfile;
import com.skillgapmanager.model.SkillProfileRequest;
import com.skillgapmanager.model.SkillProfileResponse;
import com.skillgapmanager.repository.SkillProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillProfileService {
    private final SkillProfileRepository profiles;
    private final SkillGapService skillGapService;
    private final ObjectMapper objectMapper;

    public SkillProfileService(SkillProfileRepository profiles, SkillGapService skillGapService,
                               ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.skillGapService = skillGapService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SkillProfileResponse get(Long accountId) {
        return profiles.findByAccountId(accountId).map(this::toResponse).orElse(null);
    }

    @Transactional
    public SkillProfileResponse save(Long accountId, SkillProfileRequest request) {
        Map<String, List<String>> knownRoles = skillGapService.getRoles();
        if (!knownRoles.containsKey(request.job())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown target role");
        }

        Map<String, List<String>> validatedSkills = new LinkedHashMap<>();
        request.skillsByJob().forEach((job, selectedSkills) -> {
            List<String> availableSkills = knownRoles.get(job);
            if (availableSkills == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown target role in saved skills");
            }
            if (selectedSkills == null || selectedSkills.stream().anyMatch(skill -> !availableSkills.contains(skill))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected skill is not available for its role");
            }
            validatedSkills.put(job, selectedSkills.stream().distinct().toList());
        });

        String skillsJson = writeSkills(validatedSkills);
        SkillProfile profile = profiles.findByAccountId(accountId).orElseGet(() ->
                new SkillProfile(accountId, request.name(), request.education(), request.branch(), request.year(),
                        request.experience(), request.job(), skillsJson));
        profile.update(request.name(), request.education(), request.branch(), request.year(),
                request.experience(), request.job(), skillsJson);
        return toResponse(profiles.save(profile));
    }

    private SkillProfileResponse toResponse(SkillProfile profile) {
        return new SkillProfileResponse(profile.getName(), profile.getEducation(), profile.getBranch(),
                profile.getYear(), profile.getExperience(), profile.getTargetRole(),
                readSkills(profile.getSkillsByJobJson()));
    }

    private String writeSkills(Map<String, List<String>> skills) {
        try {
            return objectMapper.writeValueAsString(skills);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the saved skill profile", exception);
        }
    }

    private Map<String, List<String>> readSkills(String skillsJson) {
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the saved skill profile", exception);
        }
    }
}
