package smartosc.conghung.modules.transfer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.common.response.ApiResult;

import java.util.Map;

@RestControllerAdvice
@Order(1)
@Slf4j
public class TransferExceptionHandler {

    @ExceptionHandler(BankTransferException.class)
    public ResponseEntity<ApiResult<Void>> handleBankTransferException(BankTransferException ex) {

        log.warn("BankTransferException: {}", ex.getMessage());

        ApiResult<Void> response = ApiResult.error(ex.getMessage(), Map.of("code", ErrorCode.PARTNER_BANK_ERROR.getCode()));

        return ResponseEntity.status(ErrorCode.PARTNER_BANK_ERROR.getHttpStatus()).body(response);
    }
}
