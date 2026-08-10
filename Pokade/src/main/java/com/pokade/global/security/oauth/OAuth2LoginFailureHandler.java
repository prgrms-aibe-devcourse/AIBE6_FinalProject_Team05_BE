package com.pokade.global.security.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String redirectBase;

    public OAuth2LoginFailureHandler(
            @Value("${app.oauth2.redirect-base}") String redirectBase) {
        this.redirectBase = redirectBase;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String reason = "oauth2_failed";
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            reason = oauthException.getError().getErrorCode();
        }
        response.sendRedirect(redirectBase + "/login?error=" + URLEncoder.encode(reason, StandardCharsets.UTF_8));
    }
}
