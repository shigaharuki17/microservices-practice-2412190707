package com.zjgsu.hly.client;

import com.zjgsu.hly.error.BusinessException;
import java.net.URI;
import java.util.List;
import java.util.Arrays;
import com.zjgsu.hly.dto.PortalRequests.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;

@Component
public class DependencyClient {
    private static final Logger log=LoggerFactory.getLogger(DependencyClient.class);
    private final DiscoveryClient discoveryClient;
    private final RestTemplate http;
    private final String userService, technicianService;
    public record ResidentInfo(Long id, String name, String phone) {}
    public record TechnicianInfo(Long id, String name, String phone, String skill, boolean available) {}
    public DependencyClient(DiscoveryClient discoveryClient,
            @Value("${dependencies.user-service:user-service}") String userService,
            @Value("${dependencies.technician-service:technician-service}") String technicianService,
            @Value("${dependencies.connect-timeout-ms:2000}") int connectMs,
            @Value("${dependencies.read-timeout-ms:3000}") int readMs) {
        this.discoveryClient=discoveryClient;
        this.userService=userService; this.technicianService=technicianService;
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectMs)).build();
        var factory=new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(readMs));
        http=new RestTemplate(factory);
    }
    public ResidentInfo resident(Long id) { return get(userService,"/api/residents/"+id,ResidentInfo.class); }
    public TechnicianInfo technician(Long id) { return get(technicianService,"/api/technicians/"+id,TechnicianInfo.class); }
    public List<ResidentInfo> residents() { return Arrays.asList(get(userService,"/api/residents",ResidentInfo[].class)); }
    public List<TechnicianInfo> technicians() { return Arrays.asList(get(technicianService,"/api/technicians",TechnicianInfo[].class)); }
    public ResidentInfo createResident(ResidentCreate request) {
        return exchange(userService,"/api/residents",HttpMethod.POST,request,ResidentInfo.class);
    }
    public TechnicianInfo createTechnician(TechnicianCreate request) {
        return exchange(technicianService,"/api/technicians",HttpMethod.POST,request,TechnicianInfo.class);
    }
    private URI discover(String serviceName,String path) {
        try {
            // Resolve every operation; never cache a fixed endpoint in this client.
            var instances=discoveryClient.getInstances(serviceName);
            if(instances.isEmpty()) throw new BusinessException(503,"依赖服务没有可用实例: "+serviceName);
            var uri=instances.getFirst().getUri().resolve(path);
            log.info("NACOS_DISCOVERY service={} uri={}",serviceName,uri);
            return uri;
        } catch(BusinessException e) { throw e; }
        catch(RuntimeException e) { throw new BusinessException(503,"服务发现暂不可用: "+serviceName); }
    }
    private <T> T get(String serviceName,String path,Class<T> type) {
        return exchange(serviceName,path,HttpMethod.GET,null,type);
    }
    private <T> T exchange(String serviceName,String path,HttpMethod method,Object body,Class<T> type) {
        URI uri=discover(serviceName,path);
        try {
            T result=http.exchange(uri,method,body==null?HttpEntity.EMPTY:new HttpEntity<>(body),type).getBody();
            if(result==null) throw new BusinessException(502,"依赖服务返回空响应");
            return result;
        } catch(HttpClientErrorException.NotFound e) {
            throw new BusinessException(404,"依赖资源不存在");
        } catch(HttpClientErrorException.Conflict e) {
            throw new BusinessException(409,"联系电话已存在或数据冲突，请核对后重试");
        } catch(HttpClientErrorException.BadRequest e) {
            throw new BusinessException(400,"请求参数不合法");
        } catch(RestClientResponseException e) {
            throw new BusinessException(502,"依赖服务返回错误响应");
        } catch(RestClientException e) {
            throw new BusinessException(503,"依赖服务不可用或请求超时");
        }
    }
}
