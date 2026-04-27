package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.dto.response.AccountBalanceResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;

import java.math.BigDecimal;

public interface AccountService {

    AccountBalanceResponseDto getBalance(String accountNumber);

    Account findByAccountNumber(String accountNumber);

    void debit(Account account, BigDecimal amount);

    void credit(Account account, BigDecimal amount);
}
