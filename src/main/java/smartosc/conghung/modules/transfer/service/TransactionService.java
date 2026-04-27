package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;

import java.math.BigDecimal;

public interface TransactionService {

    TransferResponseDto createTransaction(Account fromAccount, Account toAccount, BigDecimal amount, String requestId, String status);
}
