package com.pokade.domain.ai.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Vision API 호출 전 이미지를 축소해 토큰 사용량을 줄인다.
 * S3에 저장되는 원본에는 영향을 주지 않고, Vision 전송용 사본만 생성한다.
 */
@Component
public class ImageResizer {

    private static final int MAX_DIMENSION = 1024;

    public ByteArrayResource resizeForVision(MultipartFile file) throws IOException {
        BufferedImage original = ImageIO.read(file.getInputStream());
        if (original == null) {
            throw new IOException("이미지를 읽을 수 없습니다: " + file.getOriginalFilename());
        }

        int width = original.getWidth();
        int height = original.getHeight();
        if (Math.max(width, height) <= MAX_DIMENSION) {
            return toJpegResource(original);
        }

        double scale = (double) MAX_DIMENSION / Math.max(width, height);
        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return toJpegResource(resized);
    }

    private ByteArrayResource toJpegResource(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new ByteArrayResource(out.toByteArray());
    }
}
