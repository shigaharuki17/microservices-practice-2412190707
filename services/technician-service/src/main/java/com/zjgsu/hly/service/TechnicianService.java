package com.zjgsu.hly.service;
import com.zjgsu.hly.model.Technician;
import com.zjgsu.hly.repository.TechnicianRepository;
import com.zjgsu.hly.dto.TechnicianDto.*;
import com.zjgsu.hly.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;
@Service
@Transactional(readOnly=true)
public class TechnicianService {
    private final TechnicianRepository repository;
    public TechnicianService(TechnicianRepository repository) { this.repository=repository; }
    @Transactional public View create(Create r) {
        if(repository.existsByPhone(r.phone().trim())) throw new BusinessException(409,"联系电话已存在");
        var t=new Technician(); t.name=r.name().trim(); t.phone=r.phone().trim(); t.skill = r.skill(); t.available = r.available();
        return View.from(repository.saveAndFlush(t));
    }
    public View get(Long id) { return View.from(repository.findById(id).orElseThrow(()->new BusinessException(404,"资源不存在"))); }
    public List<View> list() { return repository.findAll(Sort.by("id")).stream().map(View::from).toList(); }
}
