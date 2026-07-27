package com.pokade.domain.ai.service;

import com.pokade.domain.ai.dto.GradeRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Vision API 호출 전 이미지 품질을 로컬에서 1차 검사한다.
 * 명백히 실패할 사진(해상도 부족, 파일 손상, 과다/과소 노출, 흐림)을 걸러내
 * 불필요한 Vision 호출(토큰 낭비)을 막는 것이 목적이며, 애매한 케이스는 그대로 Vision에 맡긴다.
 */
@Component
public class ImageQualityChecker {

    private static final int MIN_DIMENSION = 300;
    private static final int ANALYSIS_MAX_DIMENSION = 400; // 분석 성능을 위한 축소 크기 (전송/저장용 아님)
    private static final double MIN_MEAN_BRIGHTNESS = 30.0;   // 0~255 기준, 이보다 낮으면 과소노출
    private static final double MAX_MEAN_BRIGHTNESS = 225.0;  // 이보다 높으면 과다노출
    private static final double BLUR_VARIANCE_THRESHOLD = 50.0; // 라플라시안 분산 임계값 — 실측 데이터로 튜닝 필요

    public Optional<String> checkAll(GradeRequest request) {
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("앞면", request.front());
        files.put("뒷면", request.back());
        files.put("좌상단 모서리", request.cornerTl());
        files.put("우상단 모서리", request.cornerTr());
        files.put("좌하단 모서리", request.cornerBl());
        files.put("우하단 모서리", request.cornerBr());

        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            Optional<String> reason = check(entry.getValue());
            if (reason.isPresent()) {
                return Optional.of(entry.getKey() + " 사진: " + reason.get());
            }
        }
        return Optional.empty();
    }

    private Optional<String> check(MultipartFile file) {
        BufferedImage image;
        try {
            image = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            return Optional.of("이미지 파일을 읽을 수 없습니다.");
        }
        if (image == null) {
            return Optional.of("지원하지 않는 이미지 형식입니다.");
        }
        if (image.getWidth() < MIN_DIMENSION || image.getHeight() < MIN_DIMENSION) {
            return Optional.of("해상도가 너무 낮습니다 (%dx%d).".formatted(image.getWidth(), image.getHeight()));
        }

        BufferedImage analysisTarget = scaleDownForAnalysis(image);
        double[][] gray = toGrayscale(analysisTarget);

        double meanBrightness = mean(gray);
        if (meanBrightness < MIN_MEAN_BRIGHTNESS) {
            return Optional.of("사진이 너무 어둡습니다.");
        }
        if (meanBrightness > MAX_MEAN_BRIGHTNESS) {
            return Optional.of("사진이 과다 노출되었습니다.");
        }

        double blurVariance = laplacianVariance(gray);
        if (blurVariance < BLUR_VARIANCE_THRESHOLD) {
            return Optional.of("사진 초점이 흐립니다.");
        }

        return Optional.empty();
    }

    private BufferedImage scaleDownForAnalysis(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (Math.max(width, height) <= ANALYSIS_MAX_DIMENSION) {
            return image;
        }
        double scale = (double) ANALYSIS_MAX_DIMENSION / Math.max(width, height);
        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    private double[][] toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] gray = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }
        return gray;
    }

    private double mean(double[][] values) {
        double sum = 0;
        int count = 0;
        for (double[] row : values) {
            for (double v : row) {
                sum += v;
                count++;
            }
        }
        return sum / count;
    }

    // 라플라시안 커널의 응답 분산으로 초점(선명도)을 추정한다.
    // 분산이 낮을수록 강한 엣지가 적다는 뜻이며, 이는 흐린 사진의 특징이다.
    private double laplacianVariance(double[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        if (height < 3 || width < 3) {
            return Double.MAX_VALUE; // 분석하기엔 너무 작은 이미지 — 흐림 판정 skip
        }

        double sum = 0;
        double sumSq = 0;
        int count = 0;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double laplacian = -4 * gray[y][x]
                        + gray[y - 1][x] + gray[y + 1][x]
                        + gray[y][x - 1] + gray[y][x + 1];
                sum += laplacian;
                sumSq += laplacian * laplacian;
                count++;
            }
        }

        double mean = sum / count;
        return (sumSq / count) - (mean * mean);
    }
}
