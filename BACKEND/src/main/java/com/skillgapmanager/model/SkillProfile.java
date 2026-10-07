package com.skillgapmanager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill_profiles")
public class SkillProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String education;

    @Column(nullable = false, length = 80)
    private String branch;

    @Column(name = "study_year", nullable = false, length = 40)
    private String year;

    @Column(nullable = false, length = 40)
    private String experience;

    @Column(name = "target_role", nullable = false, length = 80)
    private String targetRole;

    @Column(name = "skills_by_job", nullable = false, columnDefinition = "text")
    private String skillsByJobJson;

    protected SkillProfile() {
    }

    public SkillProfile(Long accountId, String name, String education, String branch, String year,
                        String experience, String targetRole, String skillsByJobJson) {
        this.accountId = accountId;
        update(name, education, branch, year, experience, targetRole, skillsByJobJson);
    }

    public void update(String name, String education, String branch, String year,
                       String experience, String targetRole, String skillsByJobJson) {
        this.name = name;
        this.education = education;
        this.branch = branch;
        this.year = year;
        this.experience = experience;
        this.targetRole = targetRole;
        this.skillsByJobJson = skillsByJobJson;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getEducation() {
        return education;
    }

    public String getBranch() {
        return branch;
    }

    public String getYear() {
        return year;
    }

    public String getExperience() {
        return experience;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getSkillsByJobJson() {
        return skillsByJobJson;
    }
}
