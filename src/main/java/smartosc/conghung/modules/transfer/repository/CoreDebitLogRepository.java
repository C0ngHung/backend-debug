package smartosc.conghung.modules.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smartosc.conghung.modules.transfer.entity.CoreDebitLog;

public interface CoreDebitLogRepository extends JpaRepository<CoreDebitLog, Long> {

    boolean existsByCoreIdempotencyKey(String coreIdempotencyKey);
}
