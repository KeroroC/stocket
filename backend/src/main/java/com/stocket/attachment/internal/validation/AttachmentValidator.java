package com.stocket.attachment.internal.validation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.tika.Tika;

import com.stocket.attachment.internal.storage.LocalAttachmentStore;

public final class AttachmentValidator {
    private final long maxSize;
    private final long maxPixels;
    private final Tika tika = new Tika();

    public AttachmentValidator(long maxSize, long maxPixels) {
        this.maxSize = maxSize;
        this.maxPixels = maxPixels;
    }

    public ValidatedUpload validate(Path path, String originalFilename, String declaredMediaType,
                                    long size, String sha256) throws IOException {
        if (size == 0) throw problem("ATTACHMENT_EMPTY");
        if (size > maxSize) throw problem("ATTACHMENT_TOO_LARGE");
        byte[] bytes = Files.readAllBytes(path);
        String signatureType = signature(bytes);
        if (signatureType == null) throw problem("ATTACHMENT_TYPE_NOT_ALLOWED");
        if (containsActiveMarkup(bytes)) throw problem("ATTACHMENT_POLYGLOT");
        String detected = normalize(tika.detect(path));
        if (!MediaTypePolicy.ALLOWED.contains(detected) || !signatureType.equals(detected)) {
            throw problem("ATTACHMENT_INVALID_CONTENT");
        }
        if (declaredMediaType != null && !declaredMediaType.isBlank()
                && !normalize(declaredMediaType).equals(detected)) {
            throw problem("ATTACHMENT_MEDIA_TYPE_MISMATCH");
        }
        Integer width = null;
        Integer height = null;
        if (detected.equals("image/jpeg") || detected.equals("image/png")) {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw problem("ATTACHMENT_INVALID_CONTENT");
            width = image.getWidth(); height = image.getHeight();
            checkPixels(width, height);
        } else if (detected.equals("image/webp")) {
            int[] dimensions = webpDimensions(bytes);
            width = dimensions[0]; height = dimensions[1];
            checkPixels(width, height);
        } else if (!hasPdfEof(bytes)) {
            throw problem("ATTACHMENT_INVALID_CONTENT");
        }
        return new ValidatedUpload(LocalAttachmentStore.sanitizeFilename(originalFilename), detected,
                size, sha256, width, height);
    }

    private String signature(byte[] bytes) {
        if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8 && unsigned(bytes[2]) == 0xff) return "image/jpeg";
        if (bytes.length >= 8 && starts(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (bytes.length >= 16 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (bytes.length >= 5 && ascii(bytes, 0, "%PDF-")) return "application/pdf";
        return null;
    }

    private int[] webpDimensions(byte[] bytes) {
        if (ascii(bytes, 12, "VP8X") && bytes.length >= 30) {
            return new int[]{read24(bytes, 24) + 1, read24(bytes, 27) + 1};
        }
        if (ascii(bytes, 12, "VP8 ") && bytes.length >= 30
                && unsigned(bytes[23]) == 0x9d && unsigned(bytes[24]) == 0x01 && unsigned(bytes[25]) == 0x2a) {
            return new int[]{read16(bytes, 26) & 0x3fff, read16(bytes, 28) & 0x3fff};
        }
        if (ascii(bytes, 12, "VP8L") && bytes.length >= 25 && unsigned(bytes[20]) == 0x2f) {
            int dimensions = read32(bytes, 21);
            return new int[]{(dimensions & 0x3fff) + 1, ((dimensions >>> 14) & 0x3fff) + 1};
        }
        throw problem("ATTACHMENT_INVALID_CONTENT");
    }

    private boolean hasPdfEof(byte[] bytes) {
        int start = Math.max(0, bytes.length - 1024);
        return new String(bytes, start, bytes.length - start, StandardCharsets.ISO_8859_1).contains("%%EOF");
    }

    private boolean containsActiveMarkup(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return content.contains("<script") || content.contains("<html") || content.contains("<svg") || content.contains("<!doctype html");
    }

    private void checkPixels(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > maxPixels) throw problem("ATTACHMENT_IMAGE_TOO_LARGE");
    }

    private String normalize(String type) {
        int separator = type.indexOf(';');
        return (separator >= 0 ? type.substring(0, separator) : type).strip().toLowerCase(Locale.ROOT);
    }

    private boolean starts(byte[] bytes, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    private int read24(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8 | unsigned(bytes[offset + 2]) << 16;
    }

    private int read16(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8;
    }

    private int read32(byte[] bytes, int offset) {
        return read16(bytes, offset) | read16(bytes, offset + 2) << 16;
    }

    private int unsigned(byte value) { return value & 0xff; }
    private AttachmentValidationException problem(String code) { return new AttachmentValidationException(code); }
}
