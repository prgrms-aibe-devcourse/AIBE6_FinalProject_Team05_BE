package com.pokade.global.security.oauth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService  {

    // provider userinfo를 신원 attribute로 정규화 한다 (Kakao의 중첩 email을 최상위로 평탄화)

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if ("kakao".equals(registrationId)) {
            return flattenKakaoEmail(user, userRequest);
        }
        return user; // google 등은 최상위 email이라 그대로
    }

    // kakao_account.email 을 최상위 email attribute로 끌어올린다.
    private OAuth2User flattenKakaoEmail(OAuth2User user, OAuth2UserRequest userRequest) {
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        Object kakaoAccount = attributes.get("kakao_account");
        if (kakaoAccount instanceof Map<?, ?> account) {
            Object email = account.get("email");
            if (email != null) {
                attributes.put("email", email);
            }
        }
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttributeKey);
    }
}
