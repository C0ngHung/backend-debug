package smartosc.conghung.modules.transfer.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountBalanceResponseDto {

    private String accountNumber;

    private String ownerName;

    private BigDecimal balance;

    private String status;
}
