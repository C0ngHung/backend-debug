package smartosc.conghung.common.security.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.common.response.ApiResult;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Handles 403 Forbidden responses when authenticated user lacks permissions.
 * Replaces @ExceptionHandler(AccessDeniedException.class) in GlobalExceptionHandler.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied to {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResult<Void> body = ApiResult.error(
                ErrorCode.UNAUTHORIZED.getMessage(),
                Map.of("code", ErrorCode.UNAUTHORIZED.getCode())
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
