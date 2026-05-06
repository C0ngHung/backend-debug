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
@Table(name = "CORE_DEBIT_LOG")
@Getter
@Setter
@NoArgsConstructor
public class CoreDebitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_ref", nullable = false, length = 32)
    private String businessRef;

    @Column(name = "core_idempotency_key", nullable = false, unique = true, length = 128)
    private String coreIdempotencyKey;

    @Column(name = "account_no", nullable = false, length = 20)
    private String accountNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static CoreDebitLog of(String businessRef, String coreIdempotencyKey,
                                  String accountNo, BigDecimal amount) {
        CoreDebitLog log = new CoreDebitLog();
        log.businessRef = businessRef;
        log.coreIdempotencyKey = coreIdempotencyKey;
        log.accountNo = accountNo;
        log.amount = amount;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
