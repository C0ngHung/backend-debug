package smartosc.conghung.modules.auth.service;

import smartosc.conghung.modules.auth.dto.request.UserCreateRequestDto;
import smartosc.conghung.modules.auth.dto.response.UserCreateResponseDto;

public interface UserService {
    UserCreateResponseDto createUser(UserCreateRequestDto request);
}
