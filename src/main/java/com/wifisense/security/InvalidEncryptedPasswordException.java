package com.wifisense.security;

public class InvalidEncryptedPasswordException extends RuntimeException {

    public InvalidEncryptedPasswordException() {
        super("El formulario expiró. Vuelve a intentarlo.");
    }
}
