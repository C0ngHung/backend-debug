package smartosc.conghung.modules.transfer.mapper;

import org.mapstruct.Mapper;
import smartosc.conghung.modules.transfer.dto.response.AccountBalanceResponseDto;
import smartosc.conghung.modules.transfer.entity.Account;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountBalanceResponseDto toBalanceResponse(Account account);
}
