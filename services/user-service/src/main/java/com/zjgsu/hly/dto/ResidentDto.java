package com.zjgsu.hly.dto;
import jakarta.validation.constraints.*;
import com.zjgsu.hly.model.Resident;
public final class ResidentDto {
    private ResidentDto() {}
    public record Create(@NotBlank @Size(max=50) String name,
        @NotBlank @Pattern(regexp="[0-9+ -]{6,20}") String phone) {}
    public record View(Long id, String name, String phone) {
        public static View from(Resident t) { return new View(t.id, t.name, t.phone); }
    }
}
