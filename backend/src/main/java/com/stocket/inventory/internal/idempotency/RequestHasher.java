package com.stocket.inventory.internal.idempotency;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RequestHasher {

    private final ObjectMapper objectMapper;

    public RequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(UUID accountId, String operation, Object request) {
        try {
            JsonNode tree = objectMapper.valueToTree(request);
            String canonical = accountId + "\n" + operation + "\n" + canonical(tree);
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash idempotent request", exception);
        }
    }

    private String canonical(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (String name : node.propertyNames().stream().sorted(Comparator.naturalOrder()).toList()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(quote(name)).append(':').append(canonical(node.get(name)));
            }
            return result.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                result.append(canonical(node.get(index)));
            }
            return result.append(']').toString();
        }
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue().stripTrailingZeros();
            return value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.booleanValue());
        }
        return quote(node.stringValue());
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append("\\u%04x".formatted((int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append("%02x".formatted(value & 0xff));
        }
        return result.toString();
    }
}
