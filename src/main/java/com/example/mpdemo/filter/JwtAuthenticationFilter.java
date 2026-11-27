package com.example.mpdemo.filter;

import com.example.mpdemo.config.TokenBlacklist;
import com.example.mpdemo.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // 1. 检查 Token 是否在黑名单中。黑名单命中时不设置 Authentication，
            //    后续 AuthorizationFilter 会因缺少 auth 而拒绝（返回 401）。
            //    这里"放行"是让请求继续走过滤器链去被拒绝，不是放行通过。
            if (tokenBlacklist.isBlacklisted(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. 单次解析 token，避免重复 parseToken 造成的多次 JWT 验签开销
            try {
                Claims claims = jwtUtil.parseToken(token);
                // 主动检查过期（parseToken 过期会抛 ExpiredJwtException）
                if (!claims.getExpiration().after(new java.util.Date())) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Integer userId = jwtUtil.getUserIdFromClaims(claims);
                String username = jwtUtil.getUsernameFromClaims(claims);
                String role = jwtUtil.getRoleFromClaims(claims);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                authentication.setDetails(new JwtDetails(userId, username, role));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                // token 过期：跳过认证，交由 AuthorizationFilter 拒绝
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                // token 无效/格式错误：跳过认证，交由 AuthorizationFilter 拒绝
            }
        }

        filterChain.doFilter(request, response);
    }
}
