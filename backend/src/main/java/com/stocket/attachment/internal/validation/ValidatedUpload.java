package com.stocket.attachment.internal.validation;

public record ValidatedUpload(String safeFilename, String detectedMediaType, long sizeBytes,
                              String sha256, Integer width, Integer height) {}
