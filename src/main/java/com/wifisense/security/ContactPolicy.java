package com.wifisense.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Email and phone of an account, written the same way everywhere. Phones are kept in international format; a
 * Colombian mobile written as "300 123 4567" becomes "+573001234567".
 */
@Component
public class ContactPolicy {

    public static final int EMAIL_MAX = 120;
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern COLOMBIAN_MOBILE = Pattern.compile("^3[0-9]{9}$");

    /** Lower-case email, or empty when it is not a valid address. */
    public Optional<String> email(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= EMAIL_MAX && EMAIL.matcher(email).matches() ? Optional.of(email) : Optional.empty();
    }

    /** Phone in international format, or empty when it cannot be read as one. */
    public Optional<String> phone(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String digits = value.replaceAll("[\\s().-]", "");
        if (COLOMBIAN_MOBILE.matcher(digits).matches()) {
            digits = "+57" + digits;
        }
        return PHONE.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }

    public static final String EMAIL_ERROR = "Escribe un correo válido, por ejemplo nombre@gmail.com.";
    public static final String PHONE_ERROR = "Escribe el celular con indicativo, por ejemplo +57 300 123 4567.";
}
