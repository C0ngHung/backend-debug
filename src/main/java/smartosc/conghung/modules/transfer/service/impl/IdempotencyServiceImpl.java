package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.entity.AppTransferRequest;
import smartosc.conghung.modules.transfer.repository.AppTransferRequestRepository;
import smartosc.conghung.modules.transfer.service.IdempotencyService;
import smartosc.conghung.modules.transfer.vo.ExternalReference;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "IDEMPOTENCY")
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String KEY_PREFIX = "APP_TRANSFER:";

    private final AppTransferRequestRepository transferRequestRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(ExternalReference ref, TransferRequest request) {

        String idempotencyKey = KEY_PREFIX + ref.value();

        if (transferRequestRepository.existsByIdempotencyKey(idempotencyKey)) {

            log.info("Duplicate transfer detected, skipping");

            return false;
        }

        AppTransferRequest transferRequest = AppTransferRequest.processing(
                ref.value(),
                idempotencyKey,
                request.getDebitAccountNo(),
                request.getAmount()
        );

        transferRequestRepository.save(transferRequest);

        log.info("Idempotency record reserved with key: {}", idempotencyKey);

        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(ExternalReference ref) {

        String idempotencyKey = KEY_PREFIX + ref.value();

        transferRequestRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.markSuccess();
                    log.info("Transfer marked as SUCCESS");
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(ExternalReference ref) {

        String idempotencyKey = KEY_PREFIX + ref.value();

        transferRequestRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.markUnknown();
                    log.info("Transfer marked as UNKNOWN — requires reconciliation");
                });
    }
}
