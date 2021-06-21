package storagerent.fallback;

import org.springframework.stereotype.Component;

import feign.hystrix.FallbackFactory;

import storagerent.external.StorageService;



@Component
public class StorageServiceFallBackFactory  implements FallbackFactory<StorageService> {
    @Override
    public StorageService create(Throwable cause) {
       

        return new StorageService() {
            
            @Override
            public String checkStorage() {
                return "fallback";
            }

        };
    }
}
