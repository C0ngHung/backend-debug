package smartosc.conghung.modules.transfer.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smartosc.conghung.modules.transfer.exception.BankTransferException;
import smartosc.conghung.modules.transfer.service.PartnerBankApiService;

import java.math.BigDecimal;

@Service
@Slf4j(topic = "PARTNER-BANK-API")
public class PartnerBankApiServiceImpl implements PartnerBankApiService {

    @Override
    public void creditToPartnerBank(String toAccountNumber, BigDecimal amount) throws BankTransferException {

        log.info("Calling partner bank API to credit amount");

        if (toAccountNumber.startsWith("PARTNER")) {

            log.error("Partner bank rejected the transaction");

            throw new BankTransferException("Partner bank rejected the transaction: Account does not exist");
        }

        log.info("Partner bank API responded successfully");
    }
}
