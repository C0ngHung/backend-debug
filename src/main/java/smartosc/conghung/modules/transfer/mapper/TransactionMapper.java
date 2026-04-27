package smartosc.conghung.modules.transfer.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import smartosc.conghung.modules.transfer.dto.response.TransferResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;
import smartosc.conghung.modules.transfer.entity.Transaction;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "id", target = "transactionId")
    @Mapping(target = "message", constant = "Transfer completed successfully")
    TransferResponseDto toTransferResponse(Transaction transaction);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "status", target = "status")
    Transaction toEntity(Account fromAccount, Account toAccount, BigDecimal amount, String requestId, String status);
}
