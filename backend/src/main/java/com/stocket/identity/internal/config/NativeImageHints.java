package com.stocket.identity.internal.config;

import java.util.UUID;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Registers runtime hints for GraalVM Native Image compatibility.
 * Hibernate's MultiIdEntityLoaderArrayParam requires reflective
 * access to java.util.UUID[].
 */
@Configuration
@ImportRuntimeHints(NativeImageHints.Hints.class)
class NativeImageHints {

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(UUID[].class);
        }
    }
}
