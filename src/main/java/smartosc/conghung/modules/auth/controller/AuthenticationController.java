package smartosc.conghung.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.common.constant.ApiConstant;
import smartosc.conghung.common.response.ApiResult;
import smartosc.conghung.modules.auth.dto.request.LoginRequestDto;
import smartosc.conghung.modules.auth.dto.request.UserCreateRequestDto;
import smartosc.conghung.modules.auth.dto.response.LoginResponseDto;
import smartosc.conghung.modules.auth.dto.response.UserCreateResponseDto;
import smartosc.conghung.modules.auth.service.AuthenticationService;
import smartosc.conghung.modules.auth.service.UserService;

@RestController
@RequestMapping(ApiConstant.ApiAuth.BASE)
@Tag(name = "Authentication", description = "Login / Logout / Register endpoints")
@Slf4j(topic = "AUTH-CONTROLLER")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    @PostMapping(ApiConstant.ApiAuth.LOGIN)
    @Operation(summary = "User login", description = "Authenticate user and return dual tokens (access + refresh)")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ApiResult<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {

        return ApiResult.success(authenticationService.login(request));
    }

    @PostMapping(ApiConstant.ApiAuth.LOGOUT)
    @Operation(summary = "User logout", description = "Revoke token by adding to Redis blacklist")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ApiResult<Void> logout(@RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        authenticationService.logout(token);
        return ApiResult.success("Logout successful", null);
    }

    @PostMapping(ApiConstant.ApiAuth.REGISTER)
    @Operation(summary = "Register new user", description = "Create user account with BCrypt encrypted password")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data or email already exists")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ApiResult<UserCreateResponseDto> register(@Valid @RequestBody UserCreateRequestDto request) {

        return ApiResult.success(userService.createUser(request));
    }
}
