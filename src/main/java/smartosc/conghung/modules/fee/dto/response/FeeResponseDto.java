package smartosc.conghung.modules.fee.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FeeResponseDto {

    private int totalProcessed;

    private int totalSkipped;

    private Map<String, BigDecimal> feeResults;
}
