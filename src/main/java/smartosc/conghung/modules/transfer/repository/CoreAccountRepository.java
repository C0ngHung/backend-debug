package smartosc.conghung.modules.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartosc.conghung.modules.transfer.entity.CoreAccount;

import java.math.BigDecimal;

public interface CoreAccountRepository extends JpaRepository<CoreAccount, Long> {

    @Modifying
    @Query("""
            UPDATE CoreAccount account
            SET account.availableBalance = account.availableBalance - :amount,
                account.updatedAt = CURRENT_TIMESTAMP
            WHERE account.accountNo = :accountNo
              AND account.availableBalance >= :amount
            """)
    int debit(@Param("accountNo") String accountNo, @Param("amount") BigDecimal amount);
}
