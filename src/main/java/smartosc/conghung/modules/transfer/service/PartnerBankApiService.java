package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.exception.BankTransferException;

import java.math.BigDecimal;

public interface PartnerBankApiService {

    void creditToPartnerBank(String toAccountNumber, BigDecimal amount) throws BankTransferException;
}
