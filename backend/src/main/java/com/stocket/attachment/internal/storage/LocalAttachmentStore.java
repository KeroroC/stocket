package com.stocket.attachment.internal.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

public final class LocalAttachmentStore implements AttachmentStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private final Path root;
    private final Path staging;
    private final long maxSize;

    public LocalAttachmentStore(Path configuredRoot, long maxSize) throws IOException {
        Path absolute = configuredRoot.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || Files.isSymbolicLink(existing)) throw new IOException("ATTACHMENT_STORAGE_SYMLINK");
        Path canonical = existing.toRealPath(LinkOption.NOFOLLOW_LINKS).resolve(existing.relativize(absolute)).normalize();
        Files.createDirectories(canonical);
        if (Files.isSymbolicLink(canonical)) throw new IOException("ATTACHMENT_STORAGE_SYMLINK");
        this.root = canonical.toRealPath(LinkOption.NOFOLLOW_LINKS);
        this.staging = root.resolve(".staging");
        Files.createDirectories(staging);
        this.maxSize = maxSize;
    }

    @Override
    public StoredObject stage(InputStream input) throws IOException {
        String key = randomKey();
        Path temporary = safe(staging.resolve(key));
        MessageDigest digest = sha256();
        long total = 0;
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > maxSize) throw new IOException("ATTACHMENT_TOO_LARGE");
                digest.update(buffer, 0, read);
                channel.write(ByteBuffer.wrap(buffer, 0, read));
            }
            channel.force(true);
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
        return new StoredObject(key, temporary, total, HEX.formatHex(digest.digest()));
    }

    @Override
    public void commit(StoredObject object) throws IOException {
        Path target = path(object.storageKey());
        Files.createDirectories(target.getParent());
        try {
            Files.move(object.stagingPath(), target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            throw new IOException("ATTACHMENT_ATOMIC_MOVE_UNAVAILABLE", error);
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(path(storageKey), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(path(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(path(storageKey), LinkOption.NOFOLLOW_LINKS);
    }

    @Override public boolean stagedExists(String storageKey) { return Files.isRegularFile(stagedPath(storageKey), LinkOption.NOFOLLOW_LINKS); }
    @Override public void commitStaged(String storageKey) throws IOException {
        Path staged = stagedPath(storageKey);
        commit(new StoredObject(storageKey, staged, Files.size(staged), ""));
    }
    @Override public void discard(StoredObject object) throws IOException { Files.deleteIfExists(object.stagingPath()); }

    public static String sanitizeFilename(String value) {
        if (value == null) return "attachment";
        String normalized = value.replace('\\', '/').replace('∕', '/').replace('⁄', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").strip();
        if (basename.isBlank()) return "attachment";
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("ATTACHMENT_STORAGE_KEY_INVALID");
        return safe(root.resolve(key.substring(0, 2)).resolve(key.substring(2, 4)).resolve(key));
    }

    private Path stagedPath(String key) {
        if (key == null || !key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("ATTACHMENT_STORAGE_KEY_INVALID");
        return safe(staging.resolve(key));
    }

    private Path safe(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IllegalArgumentException("ATTACHMENT_PATH_INVALID");
        return normalized;
    }

    private String randomKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

}
