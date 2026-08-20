package com.pokade.domain.user.entity.type;

public enum AgreementType {
    TERMS_OF_SERVICE(true),
    PRIVACY_POLICY(true),
    THIRD_PARTY_SHARING(true),
    MARKETING(false);

    private final boolean required;

    AgreementType(boolean required) {
        this.required = required;
    }

    // 가입 시 반드시 동의해야 하는 항목인지 여부
    public boolean isRequired() {
        return required;
    }
}
