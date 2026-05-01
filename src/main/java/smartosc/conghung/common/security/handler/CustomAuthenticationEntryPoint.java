package smartosc.conghung.common.security.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.common.response.ApiResult;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Handles 401 Unauthorized responses for unauthenticated requests.
 * Replaces @ExceptionHandler(AuthenticationException.class) in GlobalExceptionHandler,
 * which cannot catch security exceptions thrown outside DispatcherServlet.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized request to {}: {}", request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResult<Void> body = ApiResult.error(
                ErrorCode.UNAUTHENTICATED.getMessage(),
                Map.of("code", ErrorCode.UNAUTHENTICATED.getCode())
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
