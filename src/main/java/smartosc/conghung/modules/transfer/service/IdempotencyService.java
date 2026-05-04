package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.vo.ExternalReference;


public interface IdempotencyService {

    boolean reserve(ExternalReference ref, TransferRequest request);

    void markSuccess(ExternalReference ref);

    void markUnknown(ExternalReference ref);
}
