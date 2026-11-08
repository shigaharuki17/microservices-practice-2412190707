package com.zjgsu.hly.dto;

import jakarta.validation.constraints.*;

public final class PortalRequests {
    private PortalRequests() {}
    public record ResidentCreate(@NotBlank @Size(max=50) String name,
            @NotBlank @Pattern(regexp="[0-9+ -]{6,20}") String phone) {}
    public record TechnicianCreate(@NotBlank @Size(max=50) String name,
            @NotBlank @Pattern(regexp="[0-9+ -]{6,20}") String phone,
            @NotNull @Pattern(regexp="PLUMBING|ELECTRICAL|LOCK") String skill,
            @NotNull Boolean available) {}
}
