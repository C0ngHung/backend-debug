package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.dto.request.CoreDebitRequest;

public interface CoreBankingService {

    void debit(CoreDebitRequest request);
}
