package smartosc.conghung.modules.auth.service;

import smartosc.conghung.modules.auth.dto.request.LoginRequestDto;
import smartosc.conghung.modules.auth.dto.response.LoginResponseDto;

public interface AuthenticationService {
    LoginResponseDto login(LoginRequestDto request);
    void logout(String token);
}
