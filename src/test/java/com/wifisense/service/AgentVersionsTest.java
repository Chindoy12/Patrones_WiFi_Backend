package com.wifisense.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVersionsTest {

    @Test
    void versionsAreComparedNumberByNumber() {
        assertThat(AgentVersions.atLeast("2.0.0", "2.0.0")).isTrue();
        assertThat(AgentVersions.atLeast("2.1.0", "2.0.0")).isTrue();
        assertThat(AgentVersions.atLeast("10.0.0", "2.0.0")).isTrue();
        assertThat(AgentVersions.atLeast("1.10.0", "1.9.0")).as("a text comparison would say no").isTrue();
        assertThat(AgentVersions.atLeast("1.9.0", "2.0.0")).isFalse();
    }

    @Test
    void missingPartsCountAsZeroAndSuffixesAreIgnored() {
        assertThat(AgentVersions.atLeast("2", "2.0.0")).isTrue();
        assertThat(AgentVersions.atLeast("2.0.0-beta", "2.0.0")).isTrue();
        assertThat(AgentVersions.atLeast("2.0.0+win", "2.0.1")).isFalse();
    }

    @Test
    void aMissingOrMalformedVersionIsOld() {
        assertThat(AgentVersions.atLeast(null, "1.0.0")).isFalse();
        assertThat(AgentVersions.atLeast(" ", "1.0.0")).isFalse();
        assertThat(AgentVersions.atLeast("x.y.z", "1.0.0")).isFalse();
    }
}
