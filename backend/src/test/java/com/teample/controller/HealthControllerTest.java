package com.teample.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void reportsUpWithoutAnyDependencies() {
        assertThat(new HealthController().health()).containsEntry("status", "UP");
    }
}
