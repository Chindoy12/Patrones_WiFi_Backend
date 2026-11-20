package com.wifisense.mail;

import com.wifisense.model.AlertType;
import org.springframework.web.util.HtmlUtils;

/** Text of an alert email, in Spanish, with what happened and how to act. */
public final class AlertEmail {

    private AlertEmail() {
    }

    /** The subject line and what to do, for one type of alert. */
    private record Wording(String title, String advice) {

        private static final String CHANGE_PASSWORD =
                "Si no lo reconoces, cambia la contraseña del Wi-Fi: así ese equipo dejará de conectarse.";

        static Wording of(AlertType type) {
            return switch (type) {
                case NEW_DEVICE -> new Wording("Equipo nuevo en tu red", CHANGE_PASSWORD);
                case UNAUTHORIZED_DEVICE -> new Wording("Un equipo que marcaste sigue conectado", CHANGE_PASSWORD);
                case SECURITY -> new Wording("La seguridad de tu red está en riesgo",
                        "Entra a WiFiSense para ver qué revisar en tu router.");
                case ANOMALY -> new Wording("Tu red se está comportando raro",
                        "Entra a WiFiSense para ver el detalle y qué puedes hacer.");
            };
        }
    }

    public static EmailMessage build(String to, String username, String networkName, AlertType type, String detail) {
        Wording wording = Wording.of(type);
        String title = wording.title();
        String advice = wording.advice();
        String text = """
                Hola, %s:

                %s (red «%s»).

                %s

                %s

                Puedes apagar estos avisos en Mi Wi-Fi > Mi cuenta.
                """.formatted(username, title, networkName, detail, advice);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1b2a41">
                  <h2 style="color:#3b6cf6">WiFiSense</h2>
                  <p>Hola, %s:</p>
                  <p><strong>%s</strong> (red «%s»).</p>
                  <p>%s</p>
                  <p style="color:#6b7a90">%s</p>
                  <p style="color:#6b7a90;font-size:13px">Puedes apagar estos avisos en Mi Wi-Fi &gt; Mi cuenta.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(username), HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(networkName), HtmlUtils.htmlEscape(detail), advice);
        return new EmailMessage(to, "WiFiSense: " + title, text, html);
    }
}
