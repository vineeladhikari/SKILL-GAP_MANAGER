package com.skillgapmanager.service;

import com.skillgapmanager.model.AnalysisRequest;
import com.skillgapmanager.model.AnalysisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SkillGapService {
    private final Map<String, List<String>> roles = createRoles();

    public Map<String, List<String>> getRoles() {
        return roles;
    }

    public AnalysisResponse analyze(AnalysisRequest request) {
        List<String> requiredSkills = roles.get(request.job());
        if (requiredSkills == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown target role: " + request.job());
        }

        Set<String> knownSkills = Set.copyOf(request.knownSkills());
        List<String> coveredSkills = requiredSkills.stream()
                .filter(knownSkills::contains)
                .toList();
        List<String> missingSkills = requiredSkills.stream()
                .filter(skill -> !knownSkills.contains(skill))
                .toList();
        int readinessPercent = Math.round(coveredSkills.size() * 100.0f / requiredSkills.size());

        return new AnalysisResponse(request.job(), coveredSkills, missingSkills, readinessPercent);
    }

    private static Map<String, List<String>> createRoles() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("Java Developer", List.of(
                "Core Java & OOP", "Collections & Generics", "Multithreading",
                "Exception handling & I/O", "SQL & JDBC", "Data Structures & Algorithms",
                "Git & GitHub", "Maven/Gradle", "Spring Boot", "REST APIs",
                "Hibernate/JPA", "JUnit testing", "Microservices basics", "Docker basics"));
        catalog.put("Python Developer", List.of(
                "Python fundamentals", "OOP in Python", "Data Structures & Algorithms",
                "SQL", "Git & GitHub", "Flask/Django", "REST APIs", "Testing (pytest)",
                "Docker basics"));
        catalog.put("Web Developer", List.of(
                "HTML & CSS", "JavaScript (ES6+)", "Responsive design", "Git & GitHub",
                "React or similar", "Node.js basics", "REST APIs", "SQL/NoSQL basics",
                "Deployment basics"));
        catalog.put("Data Analyst", List.of(
                "Excel", "SQL", "Python (pandas)", "Statistics", "Data visualization",
                "Power BI/Tableau", "Data cleaning", "Communication"));
        catalog.put("Full Stack Developer", List.of(
                "HTML, CSS, JavaScript", "React or similar", "Node.js/Express or Spring Boot",
                "REST APIs", "SQL & NoSQL", "Git & GitHub", "Authentication",
                "Docker basics", "Deployment basics"));
        return Map.copyOf(catalog);
    }
}
