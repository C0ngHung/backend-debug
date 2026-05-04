package smartosc.conghung.modules.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smartosc.conghung.modules.transfer.entity.AppTransferRequest;

public interface AppTransferRequestRepository extends JpaRepository<AppTransferRequest, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
