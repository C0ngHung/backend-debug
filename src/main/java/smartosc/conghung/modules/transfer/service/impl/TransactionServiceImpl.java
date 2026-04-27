package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;
import smartosc.conghung.modules.transfer.entity.Transaction;
import smartosc.conghung.modules.transfer.mapper.TransactionMapper;
import smartosc.conghung.modules.transfer.repository.TransactionRepository;
import smartosc.conghung.modules.transfer.service.TransactionService;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TRANSACTION-SERVICE")
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public TransferResponseDto createTransaction(Account fromAccount, Account toAccount,
                                                 BigDecimal amount, String requestId, String status) {

        Transaction transaction = transactionMapper.toEntity(fromAccount, toAccount, amount, requestId, status);

        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction record saved successfully");

        return transactionMapper.toTransferResponse(saved);
    }
}
