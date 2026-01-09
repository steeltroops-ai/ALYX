package com.alyx.gateway.config;

import com.alyx.gateway.filter.AuthenticationFilter;
import com.alyx.gateway.filter.InputValidationFilter;
import com.alyx.gateway.filter.RateLimitingFilter;
import com.alyx.gateway.filter.ReactiveAuthorizationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the API Gateway
 * 
 * Configures CORS, CSRF protection, and basic security settings
 * for the gateway. JWT authentication is handled by custom filters.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({
    PasswordSecurityProperties.class,
    AuditProperties.class,
    RateLimitingProperties.class
})
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;
    private final InputValidationFilter inputValidationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final ReactiveAuthorizationFilter reactiveAuthorizationFilter;

    public SecurityConfig(AuthenticationFilter authenticationFilter,
                         InputValidationFilter inputValidationFilter,
                         RateLimitingFilter rateLimitingFilter,
                         ReactiveAuthorizationFilter reactiveAuthorizationFilter) {
        this.authenticationFilter = authenticationFilter;
        this.inputValidationFilter = inputValidationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.reactiveAuthorizationFilter = reactiveAuthorizationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Security headers - minimal configuration for Spring Security 6.x
            .headers(headers -> headers.disable())
            
            // Add custom authorization filter
            .addFilterBefore(reactiveAuthorizationFilter, SecurityWebFiltersOrder.AUTHORIZATION)
            
            // Authorization rules - now handled by custom filter for fine-grained permission control
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                .pathMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .pathMatchers("/fallback/**").permitAll()
                .pathMatchers("/api/public/**").permitAll()
                .anyExchange().authenticated()
            )
            
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Strict CORS configuration for security
        configuration.setAllowedOriginPatterns(List.of(
            "https://localhost:3000",
            "https://localhost:5173",
            "https://*.alyx.physics.org",
            "https://*.alyx-physics.com"
        ));
        
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With",
            "X-Correlation-ID",
            "X-User-Id",
            "X-User-Role",
            "X-User-Organization",
            "X-CSRF-Token"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
            "X-Correlation-ID",
            "X-Response-Time",
            "X-RateLimit-Retry-After",
            "X-Security-Headers"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1).getSeconds());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
    
    /**
     * Security configuration properties
     */
    @Bean
    public SecurityProperties securityProperties() {
        return new SecurityProperties();
    }
    
    /**
     * Security properties configuration class
     */
    public static class SecurityProperties {
        private boolean enableStrictTransportSecurity = true;
        private boolean enableContentSecurityPolicy = true;
        private boolean enableXssProtection = true;
        private boolean enableFrameOptions = true;
        private long sessionTimeoutMinutes = 30;
        private int maxLoginAttempts = 5;
        private int lockoutDurationMinutes = 15;
        
        // Getters and setters
        public boolean isEnableStrictTransportSecurity() { return enableStrictTransportSecurity; }
        public void setEnableStrictTransportSecurity(boolean enableStrictTransportSecurity) { 
            this.enableStrictTransportSecurity = enableStrictTransportSecurity; 
        }
        
        public boolean isEnableContentSecurityPolicy() { return enableContentSecurityPolicy; }
        public void setEnableContentSecurityPolicy(boolean enableContentSecurityPolicy) { 
            this.enableContentSecurityPolicy = enableContentSecurityPolicy; 
        }
        
        public boolean isEnableXssProtection() { return enableXssProtection; }
        public void setEnableXssProtection(boolean enableXssProtection) { 
            this.enableXssProtection = enableXssProtection; 
        }
        
        public boolean isEnableFrameOptions() { return enableFrameOptions; }
        public void setEnableFrameOptions(boolean enableFrameOptions) { 
            this.enableFrameOptions = enableFrameOptions; 
        }
        
        public long getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
        public void setSessionTimeoutMinutes(long sessionTimeoutMinutes) { 
            this.sessionTimeoutMinutes = sessionTimeoutMinutes; 
        }
        
        public int getMaxLoginAttempts() { return maxLoginAttempts; }
        public void setMaxLoginAttempts(int maxLoginAttempts) { 
            this.maxLoginAttempts = maxLoginAttempts; 
        }
        
        public int getLockoutDurationMinutes() { return lockoutDurationMinutes; }
        public void setLockoutDurationMinutes(int lockoutDurationMinutes) { 
            this.lockoutDurationMinutes = lockoutDurationMinutes; 
        }
    }
}