package smartosc.conghung.modules.transfer.service;

import smartosc.conghung.modules.transfer.dto.request.TransferRequestDto;
import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.exception.BankTransferException;

public interface TransferService {

    TransferResponseDto transferToPartnerBank(TransferRequestDto request) throws BankTransferException;
}
