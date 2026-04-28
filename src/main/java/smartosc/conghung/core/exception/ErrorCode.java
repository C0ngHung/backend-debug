package smartosc.conghung.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // General
    UNCATEGORIZED(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Invalid request", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1001, "Resource not found", HttpStatus.NOT_FOUND),

    // Auth
    UNAUTHENTICATED(1100, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1101, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_TOKEN(1102, "Invalid token", HttpStatus.UNAUTHORIZED),

    // User
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS(2002, "Username already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(2003, "Email already exists", HttpStatus.CONFLICT),

    // Account
    ACCOUNT_NOT_FOUND(3001, "Account not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_BALANCE(3002, "Insufficient balance", HttpStatus.BAD_REQUEST),
    ACCOUNT_BLOCKED(3003, "Account is blocked", HttpStatus.FORBIDDEN),

    // Transfer
    TRANSFER_FAILED(4001, "Transfer failed", HttpStatus.INTERNAL_SERVER_ERROR),
    PARTNER_BANK_ERROR(4002, "Partner bank rejected the transaction", HttpStatus.BAD_GATEWAY),

    // Product
    PRODUCT_NOT_FOUND(6001, "Product not found", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS(6002, "Product already exists", HttpStatus.CONFLICT),

    // Validation
    VALIDATION_ERROR(5001, "Validation error", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
