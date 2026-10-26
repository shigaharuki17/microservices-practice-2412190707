package com.zjgsu.hly.client;
import com.zjgsu.hly.error.BusinessException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;

@Component
public class DependencyClient {
    private final RestTemplate http;
    private final String userUrl, technicianUrl;
    public record ResidentInfo(Long id, String name, String phone) {}
    public record TechnicianInfo(Long id, String name, String phone, String skill, boolean available) {}
    public DependencyClient(@Value("${dependencies.user-url}") String userUrl,
            @Value("${dependencies.technician-url}") String technicianUrl,
            @Value("${dependencies.connect-timeout-ms:2000}") int connectMs,
            @Value("${dependencies.read-timeout-ms:3000}") int readMs) {
        this.userUrl=userUrl; this.technicianUrl=technicianUrl;
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectMs)).build();
        var factory=new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(readMs));
        http=new RestTemplate(factory);
    }
    public ResidentInfo resident(Long id) { return get(userUrl+"/api/residents/"+id,ResidentInfo.class); }
    public TechnicianInfo technician(Long id) { return get(technicianUrl+"/api/technicians/"+id,TechnicianInfo.class); }
    private <T> T get(String url, Class<T> type) {
        try {
            T result=http.getForObject(url,type);
            if(result==null) throw new BusinessException(502,"依赖服务返回空响应");
            return result;
        } catch(HttpClientErrorException.NotFound e) {
            throw new BusinessException(404,"依赖资源不存在");
        } catch(RestClientResponseException e) {
            throw new BusinessException(502,"依赖服务返回错误响应");
        } catch(RestClientException e) {
            throw new BusinessException(503,"依赖服务不可用或请求超时");
        }
    }
}
