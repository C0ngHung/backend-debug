package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.modules.transfer.client.CoreBankingClient;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.entity.AppTransferRequest;
import smartosc.conghung.modules.transfer.repository.AppTransferRequestRepository;
import smartosc.conghung.modules.transfer.service.TransferService;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TRANSFER")
public class TransferServiceImpl implements TransferService {

    private final AppTransferRequestRepository transferRequestRepository;
    private final CoreBankingClient coreBankingClient;

    @Override
    @Transactional
    public void transfer(TransferRequest request) {

        String idempotencyKey = buildAppIdempotencyKey(request.getExternalRef());

        if (transferRequestRepository.existsByIdempotencyKey(idempotencyKey)) {

            log.info("Duplicate transfer detected, skipping");

            return;
        }

        AppTransferRequest transferRequest = AppTransferRequest.processing(
                request.getExternalRef(),
                idempotencyKey,
                request.getDebitAccountNo(),
                request.getAmount()
        );

        transferRequestRepository.save(transferRequest);

        coreBankingClient.debit(
                request.getExternalRef(),
                request.getDebitAccountNo(),
                request.getAmount(),
                request.getCoreDelayMillis()
        );

        transferRequest.markSuccess();

        log.info("Transfer completed successfully");
    }

    private String buildAppIdempotencyKey(String externalRef) {return "APP_TRANSFER:" + externalRef;}
}
