package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiQualityRequest;
import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.dto.ConnectionTestRequest;
import com.wifisense.model.ConnectionTest;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.ConnectionTestRepository;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.speedtest.SpeedTestLimitException;
import com.wifisense.speedtest.SpeedTestThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConnectionTestServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConnectionTestRepository tests = mock(ConnectionTestRepository.class);
    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final SpeedTestThrottle throttle = mock(SpeedTestThrottle.class);
    private final ConnectionTestService service = new ConnectionTestService(tests, networks, ai, throttle, JSON);
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", "Claro", 300);
    private final ConnectionTestRequest request = new ConnectionTestRequest(24.0, 3.0, 180.0, 40.0);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(casa, "id", 7L);
        when(networks.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(casa));
        when(tests.save(any(ConnectionTest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesTheTestWithTheGradeTheAiGave() throws Exception {
        when(ai.qualityScore(any())).thenReturn(JSON.readTree(
                "{\"score\":88,\"grade\":\"EXCELLENT\",\"gradeLabel\":\"Excelente\",\"tips\":[]}"));

        var saved = service.save("ana", 7L, request);

        assertThat(saved.qualityScore()).isEqualTo(88);
        assertThat(saved.qualityGrade()).isEqualTo("EXCELLENT");
        assertThat(saved.quality().get("gradeLabel").asText()).isEqualTo("Excelente");
        ArgumentCaptor<AiQualityRequest> sent = ArgumentCaptor.forClass(AiQualityRequest.class);
        verify(ai).qualityScore(sent.capture());
        assertThat(sent.getValue().contractedMbps()).isEqualTo(300.0);
        assertThat(sent.getValue().packetLossPct()).isNull();  // the browser test does not measure loss
        verify(throttle).acquireTest("ana");
    }

    @Test
    void keepsTheMeasurementWhenTheAiIsDown() {
        when(ai.qualityScore(any())).thenThrow(new AiServiceUnavailableException("down", null));

        var saved = service.save("ana", 7L, request);

        assertThat(saved.downloadMbps()).isEqualTo(180.0);
        assertThat(saved.qualityScore()).isNull();
        assertThat(saved.quality()).isNull();
    }

    @Test
    void ignoresAnAnswerThatDoesNotLookLikeAGrade() throws Exception {
        when(ai.qualityScore(any())).thenReturn(JSON.readTree("{\"score\":140,\"grade\":\"SUPER\"}"));

        assertThat(service.save("ana", 7L, request).qualityGrade()).isNull();
    }

    @Test
    void networksOfOthersLookLikeTheyDoNotExist() {
        when(networks.findByIdAndOwner_Username(7L, "beto")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save("beto", 7L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.history("beto", 7L, 10)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ai, tests, throttle);
    }

    @Test
    void tooManyTestsAreRefusedBeforeAskingTheAi() {
        doThrow(new SpeedTestLimitException()).when(throttle).acquireTest("ana");

        assertThatThrownBy(() -> service.save("ana", 7L, request)).isInstanceOf(SpeedTestLimitException.class);
        verifyNoInteractions(ai);
        verify(tests, never()).save(any());
    }

    @Test
    void historySizeIsBounded() {
        when(tests.findByNetworkIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of());

        service.history("ana", 7L, 5000);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(tests).findByNetworkIdOrderByCreatedAtDesc(eq(7L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(ConnectionTestService.MAX_HISTORY);
    }
}
