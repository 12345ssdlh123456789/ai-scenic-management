package com.example.mpdemo.config;

import com.example.mpdemo.entity.Result;
import com.example.mpdemo.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${jwt.allowed-origins:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // 严格白名单：不允许 "*"，仅允许配置的来源
        corsConfig.setAllowedOrigins(Arrays.asList(allowedOrigin.split(",")));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        corsConfig.setExposedHeaders(Arrays.asList("Authorization"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // CORS 仅对业务 API 路径生效，排除 actuator 等内部端点
        source.registerCorsConfiguration("/api/**", corsConfig);
        source.registerCorsConfiguration("/v1/**", corsConfig);
        source.registerCorsConfiguration("/user/**", corsConfig);
        source.registerCorsConfiguration("/order/**", corsConfig);
        source.registerCorsConfiguration("/project/**", corsConfig);
        source.registerCorsConfiguration("/vip/**", corsConfig);
        source.registerCorsConfiguration("/recharge/**", corsConfig);
        source.registerCorsConfiguration("/ai/**", corsConfig);
        source.registerCorsConfiguration("/log/**", corsConfig);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/user/login", "/v1/user/register", "/v1/user/refreshToken").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll() // 健康检查公开
                        // 项目查询公开，但只放行 GET；此前 "/v1/project/**" 让整个
                        // 项目接口面（含 POST/PUT/DELETE）都变成 permitAll，任何
                        // 未来新增的、忘了加 @PreAuthorize 的写接口都会默认公开。
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/project/list").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/project/*").permitAll()
                        .anyRequest().authenticated()
                )
                // 未认证时返回 JSON 而非 HTML
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            Result result = Result.error(401, "未授权，请先登录");
                            new ObjectMapper().writeValue(response.getWriter(), result);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            Result result = Result.error(403, "权限不足");
                            new ObjectMapper().writeValue(response.getWriter(), result);
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }
}
