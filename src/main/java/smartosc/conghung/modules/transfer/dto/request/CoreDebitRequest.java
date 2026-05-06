package smartosc.conghung.modules.transfer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CoreDebitRequest {

    @NotBlank
    private String externalRef;

    @NotBlank
    private String accountNo;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private long delayMillis;
}
