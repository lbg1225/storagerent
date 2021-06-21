package storagerent.config;

import feign.Feign;
import feign.hystrix.HystrixFeign;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpConfiguration {

    @Bean
    Feign.Builder feignBuilder() {
        return HystrixFeign.builder();
    }
}