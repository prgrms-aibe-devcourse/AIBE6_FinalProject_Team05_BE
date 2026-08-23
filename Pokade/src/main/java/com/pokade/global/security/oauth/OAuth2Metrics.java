package com.pokade.global.security.oauth;

import com.pokade.domain.user.entity.type.Provider;

import java.util.Locale;

final class OAuth2Metrics {

    static final String CALLBACK_TIMER = "auth.oauth2.callback.duration";
    static final String RESULT_COUNTER = "auth.oauth2.result";

    static final String PROVIDER_TAG = "provider";
    static final String RESULT_TAG = "result";

    // provider는 경로 세그먼트라 외부 입력이다. enum에 없는 값은 하나로 접어 카디널리티를 묶는다
    private static final String UNKNOWN = "unknown";

    private OAuth2Metrics() {
    }

    static String providerTag(String registrationId) {
        if (registrationId == null) {
            return UNKNOWN;
        }
        try {
            Provider provider = Provider.valueOf(registrationId.toUpperCase(Locale.ROOT));
            return provider == Provider.LOCAL ? UNKNOWN : provider.name().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    // 콜백 경로 /api/oauth2/callback/{registrationId}의 마지막 세그먼트를 태그값으로 쓴다.
    static String providerTagFromUri(String requestUri) {
        if (requestUri == null) {
            return UNKNOWN;
        }
        int lastSlash = requestUri.lastIndexOf('/');
        return providerTag(lastSlash < 0 ? null : requestUri.substring(lastSlash + 1));
    }
}