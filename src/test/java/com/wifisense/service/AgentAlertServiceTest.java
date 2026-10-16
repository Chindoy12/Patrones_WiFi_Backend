package com.wifisense.service;

import com.wifisense.mail.EmailMessage;
import com.wifisense.mail.MailDispatcher;
import com.wifisense.model.AgentAlert;
import com.wifisense.model.AlertPreference;
import com.wifisense.model.AlertType;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AlertPreferenceRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentAlertRepository alerts = mock(AgentAlertRepository.class);
    private final AlertPreferenceRepository preferences = mock(AlertPreferenceRepository.class);
    private final MailDispatcher mail = mock(MailDispatcher.class);
    private final AgentAlertService service = new AgentAlertService(networks, alerts, preferences, mail,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final User ana = new User("ana", "ana@gmail.com", "h", User.Role.USER);
    private final HomeNetwork casa = new HomeNetwork(ana, "Casa", null, null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ana, "id", 4L);
        ReflectionTestUtils.setField(casa, "id", 7L);
        when(networks.findById(7L)).thenReturn(Optional.of(casa));
        when(alerts.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void recordsEveryAlertAndEmailsThem() {
        service.process(7L, List.of(new AgentAlertService.Signal(AlertType.NEW_DEVICE, "Equipo nuevo")));

        verify(alerts).save(any(AgentAlert.class));
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mail).sendLater(email.capture());
        assertThat(email.getValue().to()).isEqualTo("ana@gmail.com");
        assertThat(email.getValue().subject()).contains("Equipo nuevo");
    }

    @Test
    void doesNotEmailAnAlertTheUserTurnedOff() {
        AlertPreference prefs = new AlertPreference(4L);
        prefs.set(true, false, true);  // security off
        when(preferences.findByUserId(4L)).thenReturn(Optional.of(prefs));

        service.process(7L, List.of(new AgentAlertService.Signal(AlertType.SECURITY, "Riesgo")));

        verify(alerts).save(any(AgentAlert.class));  // still recorded
        verifyNoInteractions(mail);                   // but not emailed
    }

    @Test
    void doesNotRepeatASecurityEmailWithinTheQuietWindow() {
        when(alerts.existsByNetworkIdAndTypeAndEmailedTrueAndCreatedAtAfter(eq(7L), eq(AlertType.SECURITY), any()))
                .thenReturn(true);

        service.process(7L, List.of(new AgentAlertService.Signal(AlertType.SECURITY, "Riesgo")));

        verifyNoInteractions(mail);
    }

    @Test
    void anAccountWithoutARealEmailGetsNoEmail() {
        User pepe = new User("pepe", "pepe@wifisense.local", "h", User.Role.USER);
        ReflectionTestUtils.setField(pepe, "id", 5L);
        ReflectionTestUtils.setField(casa, "owner", pepe);

        service.process(7L, List.of(new AgentAlertService.Signal(AlertType.ANOMALY, "Raro")));

        verify(alerts).save(any(AgentAlert.class));
        verifyNoInteractions(mail);
    }
}
