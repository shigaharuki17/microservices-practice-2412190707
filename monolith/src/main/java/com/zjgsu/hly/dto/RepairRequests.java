package com.zjgsu.hly.dto;
import com.zjgsu.hly.model.RepairCategory;
import jakarta.validation.constraints.*;

public final class RepairRequests {
    private RepairRequests() {}
    public record CreateTechnician(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "[0-9+ -]{6,20}") String phone,
        @NotNull RepairCategory skill, @NotNull Boolean available) {}
    public record CreateOrder(
        @NotBlank @Size(max = 50) String residentName,
        @NotBlank @Pattern(regexp = "[0-9+ -]{6,20}") String contactPhone,
        @NotBlank @Size(max = 200) String location,
        @NotBlank @Size(max = 1000) String description,
        @NotNull RepairCategory category) {}
    public record Review(@NotNull Boolean approved, @NotBlank @Size(max = 300) String note) {}
    public record TechnicianAction(@NotNull @Positive Long technicianId) {}
}
