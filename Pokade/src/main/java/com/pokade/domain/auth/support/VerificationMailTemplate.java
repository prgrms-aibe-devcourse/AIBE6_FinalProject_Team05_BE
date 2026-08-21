package com.pokade.domain.auth.support;

// 인증 코드 메일의 HTML 본문을 만든다. 메일 클라이언트가 <style> 블록과 외부 CSS를 자주
// 제거하므로 스타일을 전부 인라인 속성으로 둔다.
public final class VerificationMailTemplate {

    private static final String LAYOUT = """
            <div style="margin:0;padding:32px 16px;background-color:#F6F6F8;font-family:'Apple SD Gothic Neo',-apple-system,'Malgun Gothic',sans-serif;">
              <div style="max-width:480px;margin:0 auto;padding:32px;background-color:#FFFFFF;border:1px solid #EDEDF0;border-radius:14px;">
                <div style="font-size:15px;font-weight:800;letter-spacing:-0.3px;color:#EE1515;">POKADE</div>
                <h1 style="margin:18px 0 0;font-size:20px;font-weight:800;color:#1A1A1F;">%s</h1>
                <p style="margin:10px 0 0;font-size:14px;line-height:1.6;color:#5A5A62;">%s</p>
                <div style="margin:24px 0;padding:18px;background-color:#FDEEF0;border-radius:11px;text-align:center;font-size:30px;font-weight:800;letter-spacing:6px;color:#C21414;">%s</div>
                <p style="margin:0;font-size:13px;color:#8A8A92;">이 코드는 %d분 동안만 사용할 수 있습니다.</p>
                <hr style="margin:24px 0 0;border:none;border-top:1px solid #EDEDF0;">
                <p style="margin:16px 0 0;font-size:12px;line-height:1.6;color:#9A9AA2;">
                  본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.<br>이 메일은 발신 전용입니다.
                </p>
              </div>
            </div>
            """;

    private VerificationMailTemplate() {
    }

    // 코드 안내 메일의 HTML 본문을 만든다.
    public static String codeMail(String heading, String description, String code, int minutes) {
        return LAYOUT.formatted(heading, description, code, minutes);
    }
}
