package smartosc.conghung.modules.fee.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smartosc.conghung.modules.fee.dto.request.FeeRequestDto;
import smartosc.conghung.modules.fee.dto.response.FeeResponseDto;
import smartosc.conghung.modules.fee.service.FeeCalculationService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j(topic = "FEE-CALCULATION")
public class FeeCalculationServiceImpl implements FeeCalculationService {


    private static final BigDecimal TIER_1 = new BigDecimal("1000000");
    private static final BigDecimal TIER_2 = new BigDecimal("5000000");
    private static final BigDecimal TIER_3 = new BigDecimal("50000000");


    private static final BigDecimal FEE_FREE   = BigDecimal.ZERO;
    private static final BigDecimal FEE_LOW    = new BigDecimal("10000");
    private static final BigDecimal FEE_MEDIUM = new BigDecimal("25000");
    private static final BigDecimal FEE_HIGH   = new BigDecimal("50000");

    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount.compareTo(TIER_1) <= 0) {
            return FEE_FREE;
        } else if (amount.compareTo(TIER_2) <= 0) {
            return FEE_LOW;
        } else if (amount.compareTo(TIER_3) <= 0) {
            return FEE_MEDIUM;
        } else {
            return FEE_HIGH;
        }
    }

    @Override
    public FeeResponseDto processBatchFees(List<FeeRequestDto> requests) {
        Map<String, BigDecimal> feeResults = new HashMap<>();
        int totalSkipped = 0;

        for (FeeRequestDto request : requests) {
            BigDecimal fee = calculateFee(request.getAmount());

            BigDecimal existingFee = feeResults.get(request.getAccountNumber());

            if (existingFee != null && existingFee.equals(fee)) {

                log.info("Skip fee for txn {}: already calculated", request.getTransactionId());
                totalSkipped++;
                continue;
            }

            feeResults.put(request.getAccountNumber(), fee);
            log.info("Fee calculated for txn {}", request.getTransactionId());
        }

        FeeResponseDto response = new FeeResponseDto();
        response.setTotalProcessed(requests.size() - totalSkipped);
        response.setTotalSkipped(totalSkipped);
        response.setFeeResults(feeResults);
        return response;
    }
}
