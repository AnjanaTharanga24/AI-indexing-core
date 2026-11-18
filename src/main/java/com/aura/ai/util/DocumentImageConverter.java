package com.aura.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
public class DocumentImageConverter {

    private DocumentImageConverter() {}

    public static byte[] convertToImage(MultipartFile file) {
        try {
            String contentType = file.getContentType();

            if (contentType != null && contentType.startsWith("image/")) {
                return file.getBytes();
            }

            if (contentType != null && contentType.equals("application/pdf")) {
                return convertPdfToImage(file);
            }

            try (InputStream is = file.getInputStream()) {
                BufferedImage image = ImageIO.read(is);
                if (image != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    return baos.toByteArray();
                }
            }

            log.warn("Could not convert file to image, sending raw bytes. Content-Type: {}", contentType);
            return file.getBytes();
        } catch (Exception e) {
            log.error("Failed to convert file to image: {}", e.getMessage(), e);
            return null;
        }
    }

    public static byte[] convertPdfToImage(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            log.info("Converted PDF first page to PNG image ({} bytes)", baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to convert PDF to image: {}", e.getMessage(), e);
            return null;
        }
    }
}
