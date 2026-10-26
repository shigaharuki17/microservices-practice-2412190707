package com.zjgsu.hly.service;
import com.zjgsu.hly.model.Resident;
import com.zjgsu.hly.repository.ResidentRepository;
import com.zjgsu.hly.dto.ResidentDto.*;
import com.zjgsu.hly.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;
@Service
@Transactional(readOnly=true)
public class ResidentService {
    private final ResidentRepository repository;
    public ResidentService(ResidentRepository repository) { this.repository=repository; }
    @Transactional public View create(Create r) {
        if(repository.existsByPhone(r.phone().trim())) throw new BusinessException(409,"联系电话已存在");
        var t=new Resident(); t.name=r.name().trim(); t.phone=r.phone().trim();
        return View.from(repository.saveAndFlush(t));
    }
    public View get(Long id) { return View.from(repository.findById(id).orElseThrow(()->new BusinessException(404,"资源不存在"))); }
    public List<View> list() { return repository.findAll(Sort.by("id")).stream().map(View::from).toList(); }
}
