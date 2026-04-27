package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.core.exception.AppException;
import smartosc.conghung.core.exception.ErrorCode;
import smartosc.conghung.modules.transfer.dto.request.TransferRequestDto;
import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;
import smartosc.conghung.modules.transfer.exception.BankTransferException;
import smartosc.conghung.modules.transfer.service.AccountService;
import smartosc.conghung.modules.transfer.service.PartnerBankApiService;
import smartosc.conghung.modules.transfer.service.TransactionService;
import smartosc.conghung.modules.transfer.service.TransferService;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TRANSFER-SERVICE")
public class TransferServiceImpl implements TransferService {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PartnerBankApiService partnerBankApiService;

    @Override
    @Transactional
    public TransferResponseDto transferToPartnerBank(TransferRequestDto request) throws BankTransferException {

        Account fromAccount = accountService.findByAccountNumber(request.getFromAccountNumber());

        Account toAccount = accountService.findByAccountNumber(request.getToAccountNumber());

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        accountService.debit(fromAccount, request.getAmount());

        partnerBankApiService.creditToPartnerBank(request.getToAccountNumber(), request.getAmount());

        accountService.credit(toAccount, request.getAmount());

        return transactionService.createTransaction(fromAccount, toAccount, request.getAmount(), request.getRequestId(), "SUCCESS");
    }
}
