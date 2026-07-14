package com.stocket.attachment.internal.storage;

import java.nio.file.Path;

public record StoredObject(String storageKey, Path stagingPath, long sizeBytes, String sha256) {}
