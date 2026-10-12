package com.zjgsu.hly.service;

import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.dto.RepairViews.*;
import com.zjgsu.hly.exception.BusinessException;
import com.zjgsu.hly.model.*;
import com.zjgsu.hly.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RepairService {
    private final TechnicianRepository technicians;
    private final RepairOrderRepository orders;
    public RepairService(TechnicianRepository technicians, RepairOrderRepository orders) {
        this.technicians = technicians; this.orders = orders;
    }
    @Transactional
    public TechnicianView createTechnician(CreateTechnician request) {
        String phone = request.phone().trim();
        if (technicians.existsByPhone(phone)) throw new BusinessException(409, "维修人员联系电话已存在");
        return TechnicianView.from(technicians.saveAndFlush(new Technician(request.name().trim(), phone,
            request.skill(), request.available())));
    }
    public List<TechnicianView> technicians() {
        return technicians.findAll(Sort.by("id")).stream().map(TechnicianView::from).toList();
    }
    @Transactional
    public OrderView createOrder(CreateOrder request) {
        return OrderView.from(orders.saveAndFlush(new RepairOrder(request.residentName().trim(),
            request.contactPhone().trim(), request.location().trim(), request.description().trim(), request.category())));
    }
    public List<OrderView> orders() {
        return orders.findAll(Sort.by("id")).stream().map(OrderView::from).toList();
    }
    public OrderView order(Long id) { return OrderView.from(findOrder(id)); }
    @Transactional
    public OrderView review(Long id, Review request) {
        RepairOrder order = findOrder(id);
        requireStatus(order, OrderStatus.SUBMITTED);
        order.review(request.approved(), request.note().trim());
        return OrderView.from(orders.saveAndFlush(order));
    }
    @Transactional
    public OrderView assign(Long id, TechnicianAction request) {
        RepairOrder order = findOrder(id);
        requireStatus(order, OrderStatus.APPROVED);
        Technician technician = technicians.findById(request.technicianId())
            .orElseThrow(() -> new BusinessException(404, "维修人员不存在"));
        if (!technician.isAvailable()) throw new BusinessException(409, "维修人员当前不可接单");
        if (technician.getSkill() != order.getCategory()) throw new BusinessException(409, "维修人员技能与报修类别不匹配");
        order.assign(technician);
        return OrderView.from(orders.saveAndFlush(order));
    }
    @Transactional
    public OrderView accept(Long id, TechnicianAction request) {
        RepairOrder order = findOrder(id);
        requireStatus(order, OrderStatus.ASSIGNED);
        if (!order.getTechnician().getId().equals(request.technicianId()))
            throw new BusinessException(409, "只能由该工单被分配的维修人员接单");
        order.accept();
        return OrderView.from(orders.saveAndFlush(order));
    }
    private RepairOrder findOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> new BusinessException(404, "报修工单不存在"));
    }
    private void requireStatus(RepairOrder order, OrderStatus expected) {
        if (order.getStatus() != expected) throw new BusinessException(409,
            "当前状态 " + order.getStatus() + " 不允许此操作，需要 " + expected);
    }
}
