package smartosc.conghung.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    private boolean success;
    private String message;
    private T data;
    private Object error;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();


    public static <T> ApiResult<T> success(T data) {
        return ApiResult.<T>builder()
                .success(true)
                .message("Operation successful")
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return ApiResult.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResult<T> error(String message, Object errorDetails) {
        return ApiResult.<T>builder()
                .success(false)
                .message(message)
                .error(errorDetails)
                .build();
    }

    public static <T> ApiResult<T> error(String message) {
        return ApiResult.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
