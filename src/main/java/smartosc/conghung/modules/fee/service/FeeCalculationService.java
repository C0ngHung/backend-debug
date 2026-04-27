package smartosc.conghung.modules.fee.service;

import smartosc.conghung.modules.fee.dto.request.FeeRequestDto;
import smartosc.conghung.modules.fee.dto.response.FeeResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface FeeCalculationService {

    BigDecimal calculateFee(BigDecimal amount);

    FeeResponseDto processBatchFees(List<FeeRequestDto> requests);
}
