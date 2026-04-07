package com.istad.stadoor.tunnelserver.application.service;

import com.istad.stadoor.tunnelserver.application.dto.request.IamVerifyTokenRequest;
import com.istad.stadoor.tunnelserver.application.dto.request.LoginRequest;
import com.istad.stadoor.tunnelserver.application.dto.response.IamVerifyTokenResponse;
import com.istad.stadoor.tunnelserver.application.dto.response.LoginResponse;
import com.istad.stadoor.tunnelserver.infrastructure.config.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Service
public class AuthApplicationService {

    private static final UUID MOCK_USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final WebClient iamWebClient;
    private final AuthProperties authProperties;

    public AuthApplicationService(WebClient iamWebClient,
                                  AuthProperties authProperties) {
        this.iamWebClient = iamWebClient;
        this.authProperties = authProperties;
    }

    public LoginResponse login(LoginRequest req) {
        if (authProperties.isMockEnabled()) {
            return new LoginResponse(MOCK_USER_ID, req.token());
        }

        IamVerifyTokenResponse response = iamWebClient.post()
                .uri("/api/auth/verify")
                .bodyValue(new IamVerifyTokenRequest(req.token()))
                .retrieve()
                .bodyToMono(IamVerifyTokenResponse.class)
                .block();

        if (response == null || !response.valid() || response.userId() == null) {
            throw new SecurityException(
                    response != null && response.message() != null
                            ? response.message()
                            : "Invalid token"
            );
        }

        return new LoginResponse(response.userId(), req.token());
    }

    public UUID validateToken(String token) {
        if (authProperties.isMockEnabled()) {
            return MOCK_USER_ID;
        }

        IamVerifyTokenResponse response = iamWebClient.post()
                .uri("/api/auth/verify")
                .bodyValue(new IamVerifyTokenRequest(token))
                .retrieve()
                .bodyToMono(IamVerifyTokenResponse.class)
                .block();

        if (response == null || !response.valid() || response.userId() == null) {
            throw new SecurityException(
                    response != null && response.message() != null
                            ? response.message()
                            : "Invalid token"
            );
        }

        return response.userId();
    }
}