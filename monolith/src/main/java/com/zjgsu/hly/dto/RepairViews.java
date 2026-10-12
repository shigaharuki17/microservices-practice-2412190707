package com.zjgsu.hly.dto;
import com.zjgsu.hly.model.*;
import java.time.LocalDateTime;

public final class RepairViews {
    private RepairViews() {}
    public record TechnicianView(Long id, String name, String phone, RepairCategory skill, boolean available) {
        public static TechnicianView from(Technician t) {
            return new TechnicianView(t.getId(), t.getName(), t.getPhone(), t.getSkill(), t.isAvailable());
        }
    }
    public record OrderView(Long id, String residentName, String contactPhone, String location,
        String description, RepairCategory category, OrderStatus status, String reviewNote,
        Long technicianId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static OrderView from(RepairOrder o) {
            return new OrderView(o.getId(), o.getResidentName(), o.getContactPhone(), o.getLocation(),
                o.getDescription(), o.getCategory(), o.getStatus(), o.getReviewNote(),
                o.getTechnician() == null ? null : o.getTechnician().getId(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
