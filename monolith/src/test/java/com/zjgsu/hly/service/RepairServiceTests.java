package com.zjgsu.hly.service;

import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.dto.RepairViews.OrderView;
import com.zjgsu.hly.exception.BusinessException;
import com.zjgsu.hly.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class RepairServiceTests {
    @Autowired RepairService service;
    OrderView createOrder() {
        return service.createOrder(new CreateOrder("住户", "13900002000", "2栋201", "水管漏水", RepairCategory.PLUMBING));
    }
    void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getStatus()).isEqualTo(409));
    }
    @Test void completesSubmissionReviewAssignmentAndAcceptance() {
        var t = service.createTechnician(new CreateTechnician("维修甲", "13800002001", RepairCategory.PLUMBING, true));
        var o = createOrder();
        assertThat(o.status()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(service.review(o.id(), new Review(true, "审核通过")).status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(service.assign(o.id(), new TechnicianAction(t.id())).status()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(service.accept(o.id(), new TechnicianAction(t.id())).status()).isEqualTo(OrderStatus.ACCEPTED);
    }
    @Test void rejectsSkillMismatchWithoutChangingOrder() {
        var t = service.createTechnician(new CreateTechnician("电工", "13800002002", RepairCategory.ELECTRICAL, true));
        var o = createOrder();
        service.review(o.id(), new Review(true, "通过"));
        assertConflict(() -> service.assign(o.id(), new TechnicianAction(t.id())));
        assertThat(service.order(o.id()).status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(service.order(o.id()).technicianId()).isNull();
    }
    @Test void rejectsUnavailableTechnician() {
        var t = service.createTechnician(new CreateTechnician("休假", "13800002003", RepairCategory.PLUMBING, false));
        var o = createOrder(); service.review(o.id(), new Review(true, "通过"));
        assertConflict(() -> service.assign(o.id(), new TechnicianAction(t.id())));
    }
    @Test void rejectsAssignmentBeforeReview() {
        var t = service.createTechnician(new CreateTechnician("维修甲", "13800002004", RepairCategory.PLUMBING, true));
        var o = createOrder();
        assertConflict(() -> service.assign(o.id(), new TechnicianAction(t.id())));
    }
    @Test void rejectsWrongTechnicianAndRepeatedAcceptance() {
        var t = service.createTechnician(new CreateTechnician("维修甲", "13800002005", RepairCategory.PLUMBING, true));
        var other = service.createTechnician(new CreateTechnician("维修乙", "13800002006", RepairCategory.PLUMBING, true));
        var o = createOrder(); service.review(o.id(), new Review(true, "通过"));
        service.assign(o.id(), new TechnicianAction(t.id()));
        assertConflict(() -> service.accept(o.id(), new TechnicianAction(other.id())));
        service.accept(o.id(), new TechnicianAction(t.id()));
        assertConflict(() -> service.accept(o.id(), new TechnicianAction(t.id())));
    }
    @Test void rejectedOrderCannotBeAssigned() {
        var t = service.createTechnician(new CreateTechnician("维修甲", "13800002007", RepairCategory.PLUMBING, true));
        var o = createOrder();
        assertThat(service.review(o.id(), new Review(false, "地址不完整")).status()).isEqualTo(OrderStatus.REJECTED);
        assertConflict(() -> service.assign(o.id(), new TechnicianAction(t.id())));
    }
}
