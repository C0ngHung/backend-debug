package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.modules.transfer.dto.request.CoreDebitRequest;
import smartosc.conghung.modules.transfer.entity.CoreDebitLog;
import smartosc.conghung.modules.transfer.repository.CoreAccountRepository;
import smartosc.conghung.modules.transfer.repository.CoreDebitLogRepository;
import smartosc.conghung.modules.transfer.service.CoreBankingService;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CORE-BANKING")
public class CoreBankingServiceImpl implements CoreBankingService {

    private final CoreAccountRepository coreAccountRepository;
    private final CoreDebitLogRepository coreDebitLogRepository;

    @Override
    @Transactional
    public void debit(CoreDebitRequest request) {

        sleep(request.getDelayMillis());

        String coreIdempotencyKey = buildCoreIdempotencyKey(request.getExternalRef());

        if (coreDebitLogRepository.existsByCoreIdempotencyKey(coreIdempotencyKey)) {

            log.info("Duplicate debit detected by core, skipping");

            return;
        }

        int updatedRows = coreAccountRepository.debit(request.getAccountNo(), request.getAmount());

        if (updatedRows != 1) {throw new IllegalStateException("Core account debit failed");}

        CoreDebitLog debitLog = CoreDebitLog.of(
                request.getExternalRef(),
                coreIdempotencyKey,
                request.getAccountNo(),
                request.getAmount()
        );

        coreDebitLogRepository.save(debitLog);

        log.info("Core debit committed successfully");
    }

    private String buildCoreIdempotencyKey(String externalRef) {
        return "CORE_DEBIT:" + externalRef.strip();
    }

    private void sleep(long delayMillis) {

        if (delayMillis <= 0) {return;}

        try {

            Thread.sleep(delayMillis);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException("Core debit was interrupted", exception);
        }
    }
}
