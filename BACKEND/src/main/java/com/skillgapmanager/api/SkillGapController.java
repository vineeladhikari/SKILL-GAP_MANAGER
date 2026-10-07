package com.skillgapmanager.api;

import com.skillgapmanager.model.AnalysisRequest;
import com.skillgapmanager.model.AnalysisResponse;
import com.skillgapmanager.service.SkillGapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SkillGapController {
    private final SkillGapService skillGapService;

    public SkillGapController(SkillGapService skillGapService) {
        this.skillGapService = skillGapService;
    }

    @GetMapping("/roles")
    public Map<String, List<String>> getRoles() {
        return skillGapService.getRoles();
    }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        return skillGapService.analyze(request);
    }
}
