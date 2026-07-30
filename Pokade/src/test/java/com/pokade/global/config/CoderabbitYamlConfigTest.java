package com.pokade.global.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장소 루트의 .coderabbit.yaml 설정을 검증한다.
 * 이 설정은 CodeRabbit 리뷰 봇의 리뷰 규칙(tone_instructions, path_instructions 등)을
 * 정의하며, YAML 문법 오류나 지침 누락이 있으면 리뷰 자동화가 조용히 깨질 수 있으므로
 * 파싱 가능 여부와 팀 컨벤션 지침 내용을 함께 검증한다.
 */
class CoderabbitYamlConfigTest {

    private static String rawYaml;
    private static Map<String, Object> config;

    @BeforeAll
    static void loadConfig() throws IOException {
        Path path = resolveCoderabbitYamlPath();
        rawYaml = Files.readString(path);
        config = new Yaml().load(rawYaml);
    }

    private static Path resolveCoderabbitYamlPath() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            Path candidate = dir.resolve(".coderabbit.yaml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Could not locate .coderabbit.yaml starting from " + Paths.get("").toAbsolutePath());
    }

    @Test
    @DisplayName("유효한 YAML로 파싱되고 최상위 구조(language, tone_instructions, reviews, chat)를 모두 포함한다")
    void parsesAsValidYamlWithExpectedTopLevelKeys() {
        assertThat(config).isNotNull();
        assertThat(config).containsKeys("language", "tone_instructions", "reviews", "chat");
    }

    @Test
    @DisplayName("language는 기존 값(ko-KR)을 그대로 유지한다")
    void languageIsUnchanged() {
        assertThat(config.get("language")).isEqualTo("ko-KR");
    }

    @Test
    @DisplayName("tone_instructions는 비어있지 않은 문자열이다")
    void toneInstructionsIsNonBlankString() {
        Object toneInstructions = config.get("tone_instructions");

        assertThat(toneInstructions).isInstanceOf(String.class);
        assertThat((String) toneInstructions).isNotBlank();
    }

    @Test
    @DisplayName("tone_instructions는 폴드 블록 스칼라(>)로 작성되어 줄바꿈이 공백으로 접힌다")
    void toneInstructionsFoldsLineBreaksIntoSpaces() {
        String toneInstructions = (String) config.get("tone_instructions");

        // 원본 파일에서는 여러 줄로 나뉘어 있던 문장이 파싱 후에는 한 줄로 이어져야 한다.
        assertThat(toneInstructions).contains("팀 컨벤션 문서를 기준으로 리뷰해줘.");
        assertThat(toneInstructions)
                .contains("변수/메서드명은 camelCase, 클래스명은 PascalCase, 상수는 UPPER_SNAKE_CASE를 따르는지 확인해줘.");
        assertThat(toneInstructions).doesNotContain("\n");
    }

    @Test
    @DisplayName("tone_instructions는 네이밍 컨벤션(camelCase/PascalCase/UPPER_SNAKE_CASE) 확인 지침을 포함한다")
    void toneInstructionsCoversNamingConventions() {
        String toneInstructions = (String) config.get("tone_instructions");

        assertThat(toneInstructions).contains("camelCase");
        assertThat(toneInstructions).contains("PascalCase");
        assertThat(toneInstructions).contains("UPPER_SNAKE_CASE");
    }

    @Test
    @DisplayName("tone_instructions는 커밋 메시지 형식과 허용된 모든 type 값을 명시한다")
    void toneInstructionsCoversCommitMessageTypes() {
        String toneInstructions = (String) config.get("tone_instructions");

        assertThat(toneInstructions).contains("\"type: 작업 내용\"");
        assertThat(toneInstructions)
                .contains("feat/fix/docs/style/refactor/test/chore/design/comment/rename/remove/!HOTFIX");
        List.of("feat", "fix", "docs", "style", "refactor", "test", "chore", "design", "comment", "rename",
                        "remove", "!HOTFIX")
                .forEach(type -> assertThat(toneInstructions).contains(type));
    }

    @Test
    @DisplayName("tone_instructions는 주석/TODO 작성 규칙(불필요한 주석, 담당자/목적 없는 TODO)을 지적하도록 안내한다")
    void toneInstructionsCoversCommentAndTodoRules() {
        String toneInstructions = (String) config.get("tone_instructions");

        assertThat(toneInstructions).contains("임시 주석이 남아있거나 TODO에 담당자/목적이");
        assertThat(toneInstructions).contains("없으면 지적해줘");
    }

    @Test
    @DisplayName("tone_instructions는 디버그 출력(System.out.println) 제거와 미사용 import/변수 확인을 안내한다")
    void toneInstructionsCoversDebugOutputAndUnusedCodeRules() {
        String toneInstructions = (String) config.get("tone_instructions");

        assertThat(toneInstructions).contains("System.out.println");
        assertThat(toneInstructions).contains("사용하지 않는 import나 변수가 남아있지 않은지도 확인해줘.");
    }

    @Test
    @DisplayName("tone_instructions 추가 이후에도 reviews 섹션의 기존 설정이 손상되지 않는다")
    void reviewsSectionIsStillIntactAfterAddingToneInstructions() {
        @SuppressWarnings("unchecked")
        Map<String, Object> reviews = (Map<String, Object>) config.get("reviews");

        assertThat(reviews).isNotNull();
        assertThat(reviews.get("profile")).isEqualTo("chill");
        assertThat(reviews.get("request_changes_workflow")).isEqualTo(false);
        assertThat(reviews).containsKey("path_instructions");
    }

    @Test
    @DisplayName("tone_instructions 추가 이후에도 chat 섹션이 손상되지 않는다")
    void chatSectionIsStillIntactAfterAddingToneInstructions() {
        @SuppressWarnings("unchecked")
        Map<String, Object> chat = (Map<String, Object>) config.get("chat");

        assertThat(chat).isNotNull();
        assertThat(chat.get("auto_reply")).isEqualTo(true);
    }

    @Test
    @DisplayName("tone_instructions 키는 language와 reviews 사이, 파일 상단부에 위치한다")
    void toneInstructionsIsDeclaredBeforeReviewsSection() {
        int languageIndex = rawYaml.indexOf("language:");
        int toneInstructionsIndex = rawYaml.indexOf("tone_instructions:");
        int reviewsIndex = rawYaml.indexOf("reviews:");

        assertThat(languageIndex).isGreaterThanOrEqualTo(0);
        assertThat(toneInstructionsIndex).isGreaterThan(languageIndex);
        assertThat(reviewsIndex).isGreaterThan(toneInstructionsIndex);
    }
}