package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smartosc.conghung.core.exception.AppException;
import smartosc.conghung.core.exception.ErrorCode;
import smartosc.conghung.modules.transfer.dto.response.AccountBalanceResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;
import smartosc.conghung.modules.transfer.mapper.AccountMapper;
import smartosc.conghung.modules.transfer.repository.AccountRepository;
import smartosc.conghung.modules.transfer.service.AccountService;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "ACCOUNT-SERVICE")
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Override
    public AccountBalanceResponseDto getBalance(String accountNumber) {

        Account account = findByAccountNumber(accountNumber);

        return accountMapper.toBalanceResponse(account);
    }

    @Override
    public Account findByAccountNumber(String accountNumber) {

        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Override
    public void debit(Account account, BigDecimal amount) {

        account.setBalance(account.getBalance().subtract(amount));

        accountRepository.save(account);

        log.info("Successfully debited amount from account");
    }

    @Override
    public void credit(Account account, BigDecimal amount) {

        account.setBalance(account.getBalance().add(amount));

        accountRepository.save(account);

        log.info("Successfully credited amount to account");
    }
}
