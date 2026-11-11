package com.incident.finder.model;

import jakarta.validation.constraints.NotBlank;

public record IncidentSearchRequest(
        @NotBlank(message = "description must not be blank")
        String description
) {
}
