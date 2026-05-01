package smartosc.conghung.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import smartosc.conghung.common.response.ApiResult;

import java.util.List;
import java.util.Map;


@RestControllerAdvice
@Order(2)
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResult<Void>> handleAppException(AppException ex) {

        ErrorCode errorCode = ex.getErrorCode();

        log.error("AppException: code={}, message={}", errorCode.getCode(), ex.getMessage());

        ApiResult<Void> response = ApiResult.error(
                ex.getMessage(),
                Map.of("code", errorCode.getCode())
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid"
                ))
                .toList();

        log.warn("Validation error: {}", details);

        ApiResult<Void> response = ApiResult.error(
                ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("code", ErrorCode.VALIDATION_ERROR.getCode(), "details", details)
        );

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: ", ex);

        ApiResult<Void> response = ApiResult.error(
                ErrorCode.UNCATEGORIZED.getMessage(),
                Map.of("code", ErrorCode.UNCATEGORIZED.getCode())
        );

        return ResponseEntity
                .status(ErrorCode.UNCATEGORIZED.getHttpStatus())
                .body(response);
    }
}
