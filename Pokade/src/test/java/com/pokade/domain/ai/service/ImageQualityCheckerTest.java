package com.pokade.domain.ai.service;

import com.pokade.domain.ai.dto.GradeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImageQualityCheckerTest {

    private final ImageQualityChecker checker = new ImageQualityChecker();

    @Test
    void 해상도가_너무_낮으면_검사에_실패한다() throws IOException {
        MultipartFile tinyImage = toMultipartFile(solidColorImage(50, 50, 128, 128, 128));
        GradeRequest request = requestWithAllImages(tinyImage);

        assertThat(checker.checkAll(request)).isPresent();
    }

    @Test
    void 너무_어두운_사진은_검사에_실패한다() throws IOException {
        MultipartFile darkImage = toMultipartFile(solidColorImage(500, 500, 5, 5, 5));
        GradeRequest request = requestWithAllImages(darkImage);

        assertThat(checker.checkAll(request)).isPresent();
    }

    @Test
    void 너무_밝은_사진은_검사에_실패한다() throws IOException {
        MultipartFile brightImage = toMultipartFile(solidColorImage(500, 500, 250, 250, 250));
        GradeRequest request = requestWithAllImages(brightImage);

        assertThat(checker.checkAll(request)).isPresent();
    }

    @Test
    void 단색_이미지처럼_흐린_사진은_검사에_실패한다() throws IOException {
        // 단색 이미지는 엣지가 전혀 없어 라플라시안 분산이 0에 가까움 (흐림과 동일한 신호)
        MultipartFile flatImage = toMultipartFile(solidColorImage(500, 500, 120, 120, 120));
        GradeRequest request = requestWithAllImages(flatImage);

        assertThat(checker.checkAll(request)).isPresent();
    }

    @Test
    void 해상도_밝기_선명도가_정상이면_검사를_통과한다() throws IOException {
        MultipartFile normalImage = toMultipartFile(noiseImage(500, 500));
        GradeRequest request = requestWithAllImages(normalImage);

        assertThat(checker.checkAll(request)).isEmpty();
    }

    private BufferedImage solidColorImage(int width, int height, int r, int g, int b) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int rgb = (r << 16) | (g << 8) | b;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private BufferedImage noiseImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = 80 + random.nextInt(96); // 80~175 범위, 중간 밝기 + 높은 분산
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private MultipartFile toMultipartFile(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", out.toByteArray());
    }

    private GradeRequest requestWithAllImages(MultipartFile file) {
        return new GradeRequest(file, file, file, file, file, file, null);
    }
}
