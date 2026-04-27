package smartosc.conghung.modules.transfer.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TransferResponseDto {

    private Long transactionId;

    private String status;

    private String message;

    private LocalDateTime timestamp = LocalDateTime.now();
}
