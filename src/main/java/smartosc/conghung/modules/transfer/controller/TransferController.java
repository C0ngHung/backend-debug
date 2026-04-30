package smartosc.conghung.modules.transfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.core.dto.ApiResult;
import smartosc.conghung.modules.transfer.dto.response.AccountBalanceResponseDto;
import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.dto.request.TransferRequestDto;
import smartosc.conghung.modules.transfer.exception.BankTransferException;
import smartosc.conghung.modules.transfer.service.AccountService;
import smartosc.conghung.modules.transfer.service.TransferService;

@RestController
@RequestMapping("/api/v1/transfer")
@Tag(name = "Transfer Controller", description = "APIs for bank transfer operations")
@Slf4j(topic = "TRANSFER-CONTROLLER")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final AccountService accountService;

    @Operation(summary = "Transfer to partner bank", description = "Transfers money to a partner bank account")
    @SuppressWarnings("java:S1710")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient balance"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "502", description = "Partner bank rejected the transaction")
    })
    @PostMapping("/partner")
    public ResponseEntity<ApiResult<TransferResponseDto>> transferToPartner(
            @Valid @RequestBody TransferRequestDto request) throws BankTransferException {

        TransferResponseDto response = transferService.transferToPartnerBank(request);

        return ResponseEntity.ok(ApiResult.success("Transfer completed", response));
    }

    @Operation(summary = "Check balance", description = "Returns the current balance of an account")
    @SuppressWarnings("java:S1710")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/balance/{accountNumber}")
    public ResponseEntity<ApiResult<AccountBalanceResponseDto>> getBalance(@PathVariable String accountNumber) {

        AccountBalanceResponseDto response = accountService.getBalance(accountNumber);

        return ResponseEntity.ok(ApiResult.success(response));
    }
}

