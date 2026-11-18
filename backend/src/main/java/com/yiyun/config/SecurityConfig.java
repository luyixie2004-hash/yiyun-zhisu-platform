package com.yiyun.config;

import com.yiyun.mapper.UserMapper;
import com.yiyun.model.entity.User;
import com.yiyun.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/login", "/api/user/register").permitAll()
                        .requestMatchers("/api/user/list", "/api/user/{id}/ban", "/api/user/{id}/unban").hasRole("ADMIN")
                        .requestMatchers("/api/heritage/admin/**", "/api/booking/admin/**", "/api/comment/admin/**", "/api/inheritor/applications").hasRole("ADMIN")
                        .requestMatchers("/api/heritage/**").permitAll()
                        .requestMatchers("/api/inheritor/apply").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        if (jwtUtil.validateToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                            String username = jwtUtil.getUsernameFromToken(token);
                            // 从数据库查询当前角色，避免JWT中角色过期
                            String role = null;
                            try {
                                User user = userMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                        .eq(User::getUsername, username)
                                        .select(User::getRole)
                                );
                                if (user != null) {
                                    role = user.getRole();
                                }
                            } catch (Exception ignored) {
                                // DB查询失败时回退到JWT中的角色
                            }
                            if (role == null) {
                                role = jwtUtil.parseToken(token).get("role", String.class);
                            }
                            SecurityContextHolder.getContext().setAuthentication(
                                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                            username, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role)))
                                    )
                            );
                        }
                    } catch (Exception ignored) {
                        SecurityContextHolder.clearContext();
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }
}