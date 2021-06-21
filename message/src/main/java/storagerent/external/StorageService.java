
package storagerent.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import storagerent.config.HttpConfiguration;
import storagerent.fallback.StorageServiceFallBackFactory;

@FeignClient(name = "storage-api", url = "http://localhost:8082",
        configuration = HttpConfiguration.class,
        fallbackFactory = StorageServiceFallBackFactory.class)
public interface StorageService {

 

    @GetMapping("/checkStorage")
    String checkStorage();
}
