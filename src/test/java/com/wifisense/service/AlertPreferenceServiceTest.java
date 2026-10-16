package com.wifisense.service;

import com.wifisense.model.AlertPreference;
import com.wifisense.model.User;
import com.wifisense.repository.AlertPreferenceRepository;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertPreferenceServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AlertPreferenceRepository preferences = mock(AlertPreferenceRepository.class);
    private final AlertPreferenceService service = new AlertPreferenceService(users, preferences);
    private final User ana = new User("ana", "ana@gmail.com", "h", User.Role.USER);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ana, "id", 4L);
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
    }

    @Test
    void withoutARowEverythingIsOn() {
        when(preferences.findByUserId(4L)).thenReturn(Optional.empty());

        var prefs = service.get("ana");

        assertThat(prefs.newDevice()).isTrue();
        assertThat(prefs.security()).isTrue();
        assertThat(prefs.anomaly()).isTrue();
    }

    @Test
    void savesTheChosenSwitches() {
        when(preferences.findByUserId(4L)).thenReturn(Optional.empty());
        when(preferences.save(any())).thenAnswer(i -> i.getArgument(0));

        var prefs = service.update("ana", true, false, true);

        ArgumentCaptor<AlertPreference> saved = ArgumentCaptor.forClass(AlertPreference.class);
        verify(preferences).save(saved.capture());
        assertThat(saved.getValue().isEmailSecurity()).isFalse();
        assertThat(prefs.security()).isFalse();
    }
}
