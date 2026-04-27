package smartosc.conghung.modules.fee.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.modules.fee.dto.request.FeeRequestDto;
import smartosc.conghung.modules.fee.dto.response.FeeResponseDto;
import smartosc.conghung.modules.fee.service.FeeBatchGeneratorService;
import smartosc.conghung.modules.fee.service.FeeCalculationService;
import smartosc.conghung.core.dto.ApiResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fees")
@Tag(name = "Fee Controller", description = "APIs for transaction fee calculation — Phantom Fee Bug Demo")
@Slf4j(topic = "FEE-CONTROLLER")
@RequiredArgsConstructor
public class FeeController {

    private final FeeCalculationService feeCalculationService;
    private final FeeBatchGeneratorService feeBatchGeneratorService;

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> calculateFee(
            @RequestParam BigDecimal amount) {

        BigDecimal fee = feeCalculationService.calculateFee(amount);

        return ResponseEntity.ok(ApiResponse.success("Fee calculated", Map.of("amount", amount, "fee", fee)));
    }


    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<FeeResponseDto>> processBatch(
            @Valid @RequestBody List<FeeRequestDto> requests) {

        log.info("Processing batch of {} fee requests", requests.size());
        FeeResponseDto response = feeCalculationService.processBatchFees(requests);
        log.info("Batch complete: processed={}, skipped={}", response.getTotalProcessed(), response.getTotalSkipped());

        return ResponseEntity.ok(smartosc.conghung.core.dto.ApiResponse.success("Batch processed", response));
    }


    @GetMapping("/generate-batch")
    public ResponseEntity<ApiResponse<List<FeeRequestDto>>> generateBatch() {

        List<FeeRequestDto> batch = feeBatchGeneratorService.generateBatch();

        log.info("Generated batch of {} fee requests", batch.size());

        return ResponseEntity.ok(smartosc.conghung.core.dto.ApiResponse.success("Generated " + batch.size() + " test transactions", batch
        ));
    }
}
