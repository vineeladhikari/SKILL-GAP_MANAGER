package com.skillgapmanager.api;

import com.skillgapmanager.model.Account;
import com.skillgapmanager.model.SkillProfileRequest;
import com.skillgapmanager.model.SkillProfileResponse;
import com.skillgapmanager.service.AccountService;
import com.skillgapmanager.service.SkillProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/account/profile")
public class AccountController {
    private final AccountService accountService;
    private final SkillProfileService skillProfileService;

    public AccountController(AccountService accountService, SkillProfileService skillProfileService) {
        this.accountService = accountService;
        this.skillProfileService = skillProfileService;
    }

    @GetMapping
    public ResponseEntity<SkillProfileResponse> getProfile(Authentication authentication) {
        Account account = accountService.findByEmail(authentication.getName());
        SkillProfileResponse profile = skillProfileService.get(account.getId());
        return profile == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(profile);
    }

    @PutMapping
    public SkillProfileResponse saveProfile(Authentication authentication,
                                            @Valid @RequestBody SkillProfileRequest request) {
        Account account = accountService.findByEmail(authentication.getName());
        return skillProfileService.save(account.getId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(Authentication authentication) {
        Account account = accountService.findByEmail(authentication.getName());
        skillProfileService.delete(account.getId());
    }
}
