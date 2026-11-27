package com.example.mpdemo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 配置
 * 仅当 springdoc.api-docs.enabled=true 时生效（默认关闭），
 * 生产环境不暴露 API 文档。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("景区后台管理系统 API")
                        .description("基于 Spring Boot 3 + MyBatis-Plus + Vue 3 的景区票务管理系统")
                        .version("1.0.0")
                        .contact(new Contact().name("dazuoye")));
    }
}
