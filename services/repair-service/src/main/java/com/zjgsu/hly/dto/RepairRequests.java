package com.zjgsu.hly.dto;
import jakarta.validation.constraints.*;
public final class RepairRequests {
    private RepairRequests() {}
    public record Create(@NotNull @Positive Long residentId,
        @NotBlank @Size(max=200) String location, @NotBlank @Size(max=1000) String description,
        @NotNull @Pattern(regexp="PLUMBING|ELECTRICAL|LOCK") String category) {}
    public record Review(@NotNull Boolean approved, @NotBlank @Size(max=300) String note) {}
    public record Assign(@NotNull @Positive Long technicianId) {}
}
