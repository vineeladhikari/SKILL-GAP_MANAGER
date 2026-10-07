package com.skillgapmanager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 6, max = 64) String token,
        @NotBlank @Size(min = 4, max = 72) String password) {
}
