package com.wifisense.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsPasswordThatMeetsEveryRule() {
        assertThat(policy.passwordViolations("Redes#2026a")).isEmpty();
    }

    @Test
    void explainsEveryMissingRuleInSpanish() {
        assertThat(policy.passwordViolations("abc"))
                .containsExactly(
                        "Debe tener entre 10 y 12 caracteres.",
                        "Debe incluir al menos una letra mayúscula.",
                        "Debe incluir al menos un número.",
                        "Debe incluir al menos un carácter especial (por ejemplo ! @ # $ % *).");
    }

    @Test
    void rejectsPasswordsLongerThanTheLimit() {
        assertThat(policy.passwordViolations("Redes#2026abcdef")).containsExactly("Debe tener entre 10 y 12 caracteres.");
    }

    @Test
    void usernameIsANameOrAnEmailUpTo40Characters() {
        assertThat(policy.usernameViolations("jaider.ch")).isEmpty();
        assertThat(policy.usernameViolations("jaider.chindoy@campusucc.edu.co")).isEmpty();
        assertThat(policy.usernameViolations("a".repeat(41))).hasSize(1);
        assertThat(policy.usernameViolations("a@b@c.com")).hasSize(1);
        assertThat(policy.usernameViolations("<script>")).hasSize(1);
    }

    @Test
    void emailUsernamesAreAlsoTheAccountEmail() {
        assertThat(PasswordPolicy.emailFor("ana@uni.edu.co")).isEqualTo("ana@uni.edu.co");
        assertThat(PasswordPolicy.emailFor("ana")).isEqualTo("ana@wifisense.local");
        // the database stores emails in lowercase (V28), so the placeholder is lowercase too
        assertThat(PasswordPolicy.emailFor("Pedro")).isEqualTo("pedro@wifisense.local");
        assertThat(PasswordPolicy.emailFor("Ana@Uni.edu.co")).isEqualTo("ana@uni.edu.co");
    }

    @Test
    void oversizedLoginInputIsNotEvenLookedUp() {
        assertThat(policy.isPlausibleLogin("analyst", "Redes#2026a")).isTrue();
        assertThat(policy.isPlausibleLogin("x".repeat(5000), "Redes#2026a")).isFalse();
        assertThat(policy.isPlausibleLogin("analyst", "x".repeat(5000))).isFalse();
    }
}
