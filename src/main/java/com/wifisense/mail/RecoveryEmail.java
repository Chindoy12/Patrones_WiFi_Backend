package com.wifisense.mail;

import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/** Text of the recovery email, in Spanish, with the link and how long it lasts. */
public final class RecoveryEmail {

    private RecoveryEmail() {
    }

    public static EmailMessage build(String to, String username, String link, Duration ttl) {
        long minutes = ttl.toMinutes();
        String text = """
                Hola, %s:

                Pediste cambiar la contraseña de tu cuenta de WiFiSense. Abre este enlace para escribir una nueva:

                %s

                El enlace sirve una sola vez y vence en %d minutos. Si no fuiste tú, ignora este correo: tu
                contraseña sigue igual.
                """.formatted(username, link, minutes);
        String safeName = HtmlUtils.htmlEscape(username);
        String safeLink = HtmlUtils.htmlEscape(link);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1b2a41">
                  <h2 style="color:#3b6cf6">WiFiSense</h2>
                  <p>Hola, %s:</p>
                  <p>Pediste cambiar la contraseña de tu cuenta. Pulsa el botón para escribir una nueva.</p>
                  <p style="text-align:center;margin:28px 0">
                    <a href="%s" style="background:#3b6cf6;color:#fff;padding:12px 22px;border-radius:999px;
                       text-decoration:none;font-weight:bold">Cambiar mi contraseña</a>
                  </p>
                  <p style="color:#6b7a90;font-size:13px">El enlace sirve una sola vez y vence en %d minutos.
                  Si no fuiste tú, ignora este correo: tu contraseña sigue igual.</p>
                </div>
                """.formatted(safeName, safeLink, minutes);
        return new EmailMessage(to, "Cambia tu contraseña de WiFiSense", text, html);
    }
}
