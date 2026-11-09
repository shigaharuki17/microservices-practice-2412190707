package com.zjgsu.hly.client;
import com.zjgsu.hly.error.BusinessException;
import com.zjgsu.hly.dto.PortalRequests.*;
import feign.FeignException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Keeps the business-facing adapter stable; all remote calls now use Feign proxies. */
@Component
public class DependencyClient {
    private final ResidentFeignClient residents;
    private final TechnicianFeignClient technicians;
    public record ResidentInfo(Long id, String name, String phone) {}
    public record TechnicianInfo(Long id, String name, String phone, String skill, boolean available) {}
    public DependencyClient(ResidentFeignClient residents, TechnicianFeignClient technicians) {
        this.residents=residents; this.technicians=technicians;
    }
    public ResidentInfo resident(Long id) { return invoke(() -> residents.get(id)); }
    public TechnicianInfo technician(Long id) { return invoke(() -> technicians.get(id)); }
    public List<ResidentInfo> residents() { return invoke(residents::list); }
    public List<TechnicianInfo> technicians() { return invoke(technicians::list); }
    public ResidentInfo createResident(ResidentCreate request) { return invoke(() -> residents.create(request)); }
    public TechnicianInfo createTechnician(TechnicianCreate request) { return invoke(() -> technicians.create(request)); }
    private <T> T invoke(Supplier<T> operation) {
        try {
            T result=operation.get();
            if(result==null) throw new BusinessException(502,"依赖服务返回空响应");
            return result;
        } catch(FeignException error) {
            int remote=error.status();
            if(remote==400) throw new BusinessException(400,"请求参数不合法");
            if(remote==404) throw new BusinessException(404,"依赖资源不存在");
            if(remote==409) throw new BusinessException(409,"联系电话已存在或数据冲突，请核对后重试");
            if(remote<=0) throw new BusinessException(503,"依赖调用失败或超时，结果可能未知；写操作请先查询确认");
            if(remote==503) throw new BusinessException(503,"依赖服务没有可用实例或暂不可用");
            throw new BusinessException(502,"依赖服务返回错误响应");
        }
    }
}
