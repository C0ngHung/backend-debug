package smartosc.conghung.modules.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "APP_TRANSFER_REQUEST")
@Getter
@Setter
@NoArgsConstructor
public class AppTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 32)
    private String externalRef;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "debit_account_no", nullable = false, length = 20)
    private String debitAccountNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AppTransferRequest processing(String externalRef, String idempotencyKey,
                                                String debitAccountNo, BigDecimal amount) {
        AppTransferRequest entity = new AppTransferRequest();
        entity.externalRef = externalRef;
        entity.idempotencyKey = idempotencyKey;
        entity.debitAccountNo = debitAccountNo;
        entity.amount = amount;
        entity.status = "PROCESSING";
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        return entity;
    }

    public void markSuccess() {
        this.status = "SUCCESS";
        this.updatedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = "UNKNOWN";
        this.updatedAt = LocalDateTime.now();
    }
}
