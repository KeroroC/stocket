package com.stocket.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.stocket.attachment.internal.validation.AttachmentValidationException;
import com.stocket.attachment.internal.validation.AttachmentValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentValidatorTest {

    @TempDir Path temp;
    private final AttachmentValidator validator = new AttachmentValidator(20 * 1024 * 1024L, 40_000_000L);

    @Test
    void acceptsAllowedSignaturesAndSanitizesFilename() throws Exception {
        assertThat(validate(image("jpg"), "photo.jpg", "image/jpeg").detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(validate(image("png"), "photo.png", "image/png").detectedMediaType()).isEqualTo("image/png");
        assertThat(validate(webpExtended(2, 3), "extended.webp", "image/webp").detectedMediaType()).isEqualTo("image/webp");
        assertThat(validate(webpLossy(4, 5), "lossy.webp", "image/webp").detectedMediaType()).isEqualTo("image/webp");
        assertThat(validate(webpLossless(6, 7), "lossless.webp", "image/webp").detectedMediaType()).isEqualTo("image/webp");
        assertThat(validate("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(), "invoice.pdf", "application/pdf").detectedMediaType()).isEqualTo("application/pdf");
        assertThat(validate(image("png"), "../\u0001hidden.png", "image/png").safeFilename()).isEqualTo("hidden.png");
    }

    @Test
    void rejectsEmptyMismatchPolyglotExecutableAndBrokenPdf() throws Exception {
        assertCode(new byte[0], "empty.jpg", "image/jpeg", "ATTACHMENT_EMPTY");
        assertCode(image("png"), "wrong.jpg", "image/jpeg", "ATTACHMENT_MEDIA_TYPE_MISMATCH");
        assertCode("<svg><script>alert(1)</script></svg>".getBytes(), "script.svg", "image/svg+xml", "ATTACHMENT_TYPE_NOT_ALLOWED");
        byte[] jpeg = image("jpg");
        byte[] polyglot = ByteBuffer.allocate(jpeg.length + 20).put(jpeg).put("<script>x</script>".getBytes()).array();
        assertCode(polyglot, "polyglot.jpg", "image/jpeg", "ATTACHMENT_POLYGLOT");
        assertCode("%PDF-1.7\nno eof".getBytes(), "broken.pdf", "application/pdf", "ATTACHMENT_INVALID_CONTENT");
    }

    private com.stocket.attachment.internal.validation.ValidatedUpload validate(byte[] bytes, String name, String type) throws Exception {
        Path file = temp.resolve(java.util.UUID.randomUUID().toString());
        Files.write(file, bytes);
        return validator.validate(file, name, type, bytes.length, "a".repeat(64));
    }

    private void assertCode(byte[] bytes, String name, String type, String code) {
        assertThatThrownBy(() -> validate(bytes, name, type))
                .isInstanceOf(AttachmentValidationException.class)
                .extracting(error -> ((AttachmentValidationException) error).code())
                .isEqualTo(code);
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] webpExtended(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(22).put("WEBPVP8X".getBytes()).putInt(10).putInt(0);
        put24(buffer, width - 1); put24(buffer, height - 1);
        return buffer.array();
    }

    private byte[] webpLossy(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(22).put("WEBPVP8 ".getBytes()).putInt(10);
        buffer.put(new byte[]{0x10, 0, 0, (byte) 0x9d, 0x01, 0x2a});
        buffer.putShort((short) width).putShort((short) height);
        return buffer.array();
    }

    private byte[] webpLossless(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(18).put("WEBPVP8L".getBytes()).putInt(5).put((byte) 0x2f);
        buffer.putInt((width - 1) | ((height - 1) << 14));
        buffer.put((byte) 0);
        return buffer.array();
    }

    private void put24(ByteBuffer buffer, int value) {
        buffer.put((byte) value).put((byte) (value >>> 8)).put((byte) (value >>> 16));
    }
}
