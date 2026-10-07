package com.skillgapmanager.repository;

import com.skillgapmanager.model.SkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {
    Optional<SkillProfile> findByAccountId(Long accountId);
}
