package com.zjgsu.hly.dto;
import jakarta.validation.constraints.*;
import com.zjgsu.hly.model.Technician;
public final class TechnicianDto {
    private TechnicianDto() {}
    public record Create(@NotBlank @Size(max=50) String name,
        @NotBlank @Pattern(regexp="[0-9+ -]{6,20}") String phone, @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="PLUMBING|ELECTRICAL|LOCK") String skill, @jakarta.validation.constraints.NotNull Boolean available) {}
    public record View(Long id, String name, String phone, String skill, boolean available) {
        public static View from(Technician t) { return new View(t.id, t.name, t.phone, t.skill, t.available); }
    }
}
