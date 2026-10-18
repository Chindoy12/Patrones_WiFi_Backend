package com.wifisense.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Credential rules shared by account creation and login input checks. Messages are user-facing (Spanish). */
@Component
public class PasswordPolicy {

    public static final int USERNAME_MAX = 40;
    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX = 12;

    private static final Pattern USERNAME = Pattern.compile("^(?=.{3," + USERNAME_MAX + "}$)[A-Za-z0-9._-]+(@[A-Za-z0-9.-]+)?$");
    private static final Pattern UPPERCASE = Pattern.compile("[A-ZÁÉÍÓÚÑ]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9ÁÉÍÓÚÑáéíóúñ\\s]");

    public List<String> usernameViolations(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return List.of("El usuario debe tener entre 3 y " + USERNAME_MAX
                    + " caracteres: un correo o un nombre con letras, números, punto, guion o guion bajo.");
        }
        return List.of();
    }

    public List<String> passwordViolations(String password) {
        List<String> errors = new ArrayList<>();
        String value = password == null ? "" : password;
        if (value.length() < PASSWORD_MIN || value.length() > PASSWORD_MAX) {
            errors.add("Debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.");
        }
        if (!UPPERCASE.matcher(value).find()) {
            errors.add("Debe incluir al menos una letra mayúscula.");
        }
        if (!DIGIT.matcher(value).find()) {
            errors.add("Debe incluir al menos un número.");
        }
        if (!SPECIAL.matcher(value).find()) {
            errors.add("Debe incluir al menos un carácter especial (por ejemplo ! @ # $ % *).");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            errors.add("No debe contener espacios.");
        }
        return errors;
    }

    /** The username itself when it is an email address; otherwise an internal placeholder address. */
    public static String emailFor(String username) {
        // always lowercase: the database keeps emails in lowercase (V28) and compares them that way
        return (username.contains("@") ? username : username + com.wifisense.model.User.PLACEHOLDER_DOMAIN)
                .toLowerCase(java.util.Locale.ROOT);
    }

    /** Cheap shape check before touching the database; anything outside the limits is simply a failed login. */
    public boolean isPlausibleLogin(String username, String password) {
        return username != null && !username.isBlank() && username.length() <= USERNAME_MAX
                && password != null && !password.isEmpty() && password.length() <= PASSWORD_MAX;
    }
}
