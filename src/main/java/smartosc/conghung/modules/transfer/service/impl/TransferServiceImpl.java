package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import smartosc.conghung.modules.transfer.client.CoreBankingClient;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.service.IdempotencyService;
import smartosc.conghung.modules.transfer.service.TransferService;
import smartosc.conghung.modules.transfer.vo.ExternalReference;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TRANSFER")
public class TransferServiceImpl implements TransferService {

    private final IdempotencyService idempotencyService;
    private final CoreBankingClient coreBankingClient;

    @Override
    @Transactional
    public void transfer(TransferRequest request) {

        ExternalReference ref = ExternalReference.from(request.getExternalRef());

        boolean reserved = idempotencyService.reserve(ref, request);

        if (!reserved) {

            log.info("Duplicate transfer detected, skipping");

            return;
        }

        try {
            coreBankingClient.debit(
                    ref.value(),
                    request.getDebitAccountNo(),
                    request.getAmount(),
                    request.getCoreDelayMillis()
            );

            idempotencyService.markSuccess(ref);

            log.info("Transfer completed successfully");

        } catch (ResourceAccessException exception) {

            idempotencyService.markUnknown(ref);

            log.error("Transfer timeout — marked as UNKNOWN for reconciliation");

            throw exception;
        }
    }
}
