package com.wifisense.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContactPolicyTest {

    private final ContactPolicy policy = new ContactPolicy();

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "' Ana.Lopez@Gmail.com ', ana.lopez@gmail.com", "ana@ucc.edu.co, ana@ucc.edu.co",
            "ana@gmail, NULL", "@gmail.com, NULL", "ana gmail.com, NULL", "NULL, NULL"})
    void emails(String input, String expected) {
        assertThat(policy.email(input).orElse(null)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "300 123 4567, +573001234567", "'(300) 123-4567', +573001234567", "+57 300 123 4567, +573001234567",
            "+1 415 555 0100, +14155550100", "12345, NULL", "+0 123 456 789, NULL", "abc, NULL"})
    void phones(String input, String expected) {
        assertThat(policy.phone(input).orElse(null)).isEqualTo(expected);
    }
}
