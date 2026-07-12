package com.stocket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

import static org.assertj.core.api.Assertions.assertThat;

class StocketApplicationTests {

    @Test
    void applicationRootDeclaresBootAndModulith() {
        assertThat(StocketApplication.class)
                .hasAnnotation(SpringBootApplication.class)
                .hasAnnotation(Modulithic.class);
    }
}
