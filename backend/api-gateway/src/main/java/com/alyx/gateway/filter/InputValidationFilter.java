package com.alyx.gateway.filter;

import com.alyx.gateway.service.SecurityAuditService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation and sanitization filter
 * 
 * Validates and sanitizes all incoming requests to prevent
 * injection attacks, XSS, and other security vulnerabilities.
 */
@Component
public class InputValidationFilter implements GatewayFilter {

    private final SecurityAuditService auditService;
    
    // Security patterns for detection
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute).*"),
        Pattern.compile("(?i).*('|(\\-\\-)|(;)|(\\|)|(\\*)).*"),
        Pattern.compile("(?i).*(or|and)\\s+\\d+\\s*=\\s*\\d+.*"),
        Pattern.compile("(?i).*\\b(waitfor|delay)\\s+.*")
    );
    
    private static final List<Pattern> XSS_PATTERNS = List.of(
        Pattern.compile("(?i).*<\\s*script.*"),
        Pattern.compile("(?i).*javascript\\s*:.*"),
        Pattern.compile("(?i).*on\\w+\\s*=.*"),
        Pattern.compile("(?i).*<\\s*iframe.*"),
        Pattern.compile("(?i).*<\\s*object.*"),
        Pattern.compile("(?i).*<\\s*embed.*")
    );
    
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i).*(\\||&|;|\\$\\(|`|\\$\\{).*"),
        Pattern.compile("(?i).*(rm|cat|ls|ps|kill|chmod|sudo|su)\\s+.*"),
        Pattern.compile("(?i).*\\.\\.[\\/\\\\].*")
    );
    
    private static final List<Pattern> LDAP_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i).*(\\*|\\(|\\)|\\\\|\\/).*"),
        Pattern.compile("(?i).*(objectclass|cn|uid|ou)\\s*=.*")
    );
    
    // Content size limits
    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_HEADER_SIZE = 8192; // 8KB
    private static final int MAX_URL_LENGTH = 2048;
    
    // Allowed content types for POST/PUT requests
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "application/json",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
        "text/plain"
    );

    public InputValidationFilter(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Validate request size and headers
        if (!validateRequestSize(request)) {
            auditService.logSuspiciousActivity(
                request.getHeaders().getFirst("X-User-Id"),
                getClientIpAddress(request),
                "Request size exceeds maximum allowed limit",
                "HIGH"
            );
            return onError(exchange, "Request size exceeds maximum allowed limit", HttpStatus.REQUEST_ENTITY_TOO_LARGE);
        }
        
        // Validate URL length
        if (request.getURI().toString().length() > MAX_URL_LENGTH) {
            auditService.logSuspiciousActivity(
                request.getHeaders().getFirst("X-User-Id"),
                getClientIpAddress(request),
                "URL length exceeds maximum allowed limit",
                "MEDIUM"
            );
            return onError(exchange, "URL length exceeds maximum allowed limit", HttpStatus.REQUEST_URI_TOO_LONG);
        }
        
        // Validate headers
        if (!validateHeaders(request)) {
            auditService.logSuspiciousActivity(
                request.getHeaders().getFirst("X-User-Id"),
                getClientIpAddress(request),
                "Invalid or malicious headers detected",
                "HIGH"
            );
            return onError(exchange, "Invalid request headers", HttpStatus.BAD_REQUEST);
        }
        
        // Validate query parameters
        if (!validateQueryParameters(request)) {
            auditService.logSuspiciousActivity(
                request.getHeaders().getFirst("X-User-Id"),
                getClientIpAddress(request),
                "Malicious query parameters detected",
                "HIGH"
            );
            return onError(exchange, "Invalid query parameters", HttpStatus.BAD_REQUEST);
        }
        
        // For POST/PUT requests, validate content type and body
        if (HttpMethod.POST.equals(request.getMethod()) || HttpMethod.PUT.equals(request.getMethod())) {
            return validateRequestBody(exchange, chain);
        }
        
        return chain.filter(exchange);
    }
    
    private boolean validateRequestSize(ServerHttpRequest request) {
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                long size = Long.parseLong(contentLength);
                return size <= MAX_REQUEST_SIZE;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
    
    private boolean validateHeaders(ServerHttpRequest request) {
        return request.getHeaders().entrySet().stream().allMatch(entry -> {
            String headerName = entry.getKey();
            List<String> headerValues = entry.getValue();
            
            // Check header name length
            if (headerName.length() > 256) {
                return false;
            }
            
            // Check header values
            for (String value : headerValues) {
                if (value.length() > MAX_HEADER_SIZE) {
                    return false;
                }
                
                // Check for malicious patterns in header values
                if (containsMaliciousPattern(value)) {
                    return false;
                }
            }
            
            return true;
        });
    }
    
    private boolean validateQueryParameters(ServerHttpRequest request) {
        return request.getQueryParams().entrySet().stream().allMatch(entry -> {
            String paramName = entry.getKey();
            List<String> paramValues = entry.getValue();
            
            // Check parameter name
            if (containsMaliciousPattern(paramName)) {
                return false;
            }
            
            // Check parameter values
            for (String value : paramValues) {
                if (containsMaliciousPattern(value)) {
                    return false;
                }
            }
            
            return true;
        });
    }
    
    private Mono<Void> validateRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Validate content type
        String contentType = request.getHeaders().getFirst("Content-Type");
        if (contentType != null && !isAllowedContentType(contentType)) {
            auditService.logSuspiciousActivity(
                request.getHeaders().getFirst("X-User-Id"),
                getClientIpAddress(request),
                "Unsupported content type: " + contentType,
                "MEDIUM"
            );
            return onError(exchange, "Unsupported content type", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        
        // Read and validate request body
        return DataBufferUtils.join(request.getBody())
            .flatMap(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                
                String body = new String(bytes, StandardCharsets.UTF_8);
                
                // Validate body content
                if (containsMaliciousPattern(body)) {
                    auditService.logSuspiciousActivity(
                        request.getHeaders().getFirst("X-User-Id"),
                        getClientIpAddress(request),
                        "Malicious content detected in request body",
                        "HIGH"
                    );
                    return onError(exchange, "Invalid request content", HttpStatus.BAD_REQUEST);
                }
                
                // Create new request with validated body
                ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                    }
                };
                
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .switchIfEmpty(chain.filter(exchange));
    }
    
    private boolean containsMaliciousPattern(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        String decodedInput = urlDecode(input);
        
        // Check for SQL injection patterns
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(decodedInput).matches()) {
                return true;
            }
        }
        
        // Check for XSS patterns
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(decodedInput).matches()) {
                return true;
            }
        }
        
        // Check for command injection patterns
        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(decodedInput).matches()) {
                return true;
            }
        }
        
        // Check for LDAP injection patterns
        for (Pattern pattern : LDAP_INJECTION_PATTERNS) {
            if (pattern.matcher(decodedInput).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isAllowedContentType(String contentType) {
        return ALLOWED_CONTENT_TYPES.stream()
            .anyMatch(allowed -> contentType.toLowerCase().startsWith(allowed));
    }
    
    private String urlDecode(String input) {
        try {
            return java.net.URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return input;
        }
    }
    
    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
    
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "DENY");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        
        String body = String.format("{\"error\": \"%s\", \"status\": %d, \"timestamp\": \"%s\"}", 
            err, httpStatus.value(), java.time.Instant.now().toString());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}