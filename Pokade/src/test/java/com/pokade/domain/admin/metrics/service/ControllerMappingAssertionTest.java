package com.pokade.domain.admin.metrics.service;

import com.pokade.domain.ai.controller.AiGradeController;
import com.pokade.domain.trade.controller.TradeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// AdminMetricsService.AI_GRADE_URI/TRADE_CONFIRM_URI는 domain.ai/domain.trade 컨트롤러의 실제 매핑
// 경로와 문자 그대로 일치해야 하는데, 이건 컴파일러가 검증해주지 않는 암묵적 결합이다(경로가 바뀌면
// 어드민 지표는 에러 없이 그냥 조용히 0으로 떨어진다). 여기서 리플렉션으로 실제 컨트롤러 어노테이션 값을
// 읽어와 비교해서, 그 결합이 깨지면 이 테스트가 즉시 실패하도록 한다 - domain.ai/domain.trade 코드는
// 읽기만 하고 전혀 수정하지 않는다.
class ControllerMappingAssertionTest {

    @Test
    @DisplayName("AI_GRADE_URI가 AiGradeController.grade()의 실제 매핑과 일치한다")
    void aiGradeUri_matchesActualControllerMapping() {
        String classPrefix = AiGradeController.class.getAnnotation(RequestMapping.class).value()[0];
        Method method = findMethodByName(AiGradeController.class, "grade");
        String methodPath = method.getAnnotation(PostMapping.class).value()[0];

        assertThat(classPrefix + methodPath).isEqualTo(AdminMetricsService.AI_GRADE_URI);
    }

    @Test
    @DisplayName("TRADE_CONFIRM_URI가 TradeController.confirmTrade()의 실제 매핑과 일치한다")
    void tradeConfirmUri_matchesActualControllerMapping() {
        String classPrefix = TradeController.class.getAnnotation(RequestMapping.class).value()[0];
        Method method = findMethodByName(TradeController.class, "confirmTrade");
        String methodPath = method.getAnnotation(PatchMapping.class).value()[0];

        assertThat(classPrefix + methodPath).isEqualTo(AdminMetricsService.TRADE_CONFIRM_URI);
    }

    // 파라미터 시그니처가 바뀌어도(예: 파라미터 추가/순서 변경) 깨지지 않도록 메서드 이름만으로 찾는다.
    private static Method findMethodByName(Class<?> controllerClass, String methodName) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        controllerClass.getSimpleName() + "에 " + methodName + " 메서드가 없습니다 - 이름이 바뀌었을 수 있습니다."));
    }
}
