package com.zjgsu.hly.service;
import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.error.BusinessException;
import com.zjgsu.hly.model.RepairOrder;
import com.zjgsu.hly.repository.RepairOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;
@Service @Transactional(readOnly=true)
public class RepairService {
    private final RepairOrderRepository orders;
    private final DependencyClient dependencies;
    public RepairService(RepairOrderRepository orders,DependencyClient dependencies) {
        this.orders=orders; this.dependencies=dependencies;
    }
    @Transactional public RepairOrder create(Create r) {
        // Validate via real HTTP before any local INSERT. No cross-service Repository access.
        var resident=dependencies.resident(r.residentId());
        var o=new RepairOrder(); o.residentId=resident.id(); o.residentName=resident.name();
        o.contactPhone=resident.phone(); o.location=r.location().trim();
        o.description=r.description().trim(); o.category=r.category(); o.status="SUBMITTED";
        return orders.saveAndFlush(o);
    }
    public RepairOrder get(Long id) { return orders.findById(id).orElseThrow(()->new BusinessException(404,"工单不存在")); }
    public List<RepairOrder> list() { return orders.findAll(Sort.by("id")); }
    @Transactional public RepairOrder review(Long id,Review r) {
        var o=get(id); require(o,"SUBMITTED"); o.status=r.approved()?"APPROVED":"REJECTED";
        o.reviewNote=r.note().trim(); return orders.saveAndFlush(o);
    }
    @Transactional public RepairOrder assign(Long id,Assign r) {
        var o=get(id); require(o,"APPROVED");
        var t=dependencies.technician(r.technicianId());
        if(!t.available()) throw new BusinessException(409,"维修人员不可接单");
        if(!o.category.equals(t.skill())) throw new BusinessException(409,"技能与报修类别不匹配");
        o.technicianId=t.id(); o.status="ASSIGNED"; return orders.saveAndFlush(o);
    }
    @Transactional public RepairOrder accept(Long id,Assign r) {
        var o=get(id); require(o,"ASSIGNED");
        if(!o.technicianId.equals(r.technicianId())) throw new BusinessException(409,"接单人员不匹配");
        dependencies.technician(r.technicianId());
        o.status="ACCEPTED"; return orders.saveAndFlush(o);
    }
    private void require(RepairOrder o,String expected) {
        if(!expected.equals(o.status)) throw new BusinessException(409,"当前工单状态不允许此操作");
    }
}
