package com.wifisense.integration;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.CachingAiClient;
import com.wifisense.analysis.CircuitBreakerAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/** In the running application every service that asks the AI gets the caching proxy, which reaches the AI only through
 * the Proxy with the Circuit Breaker, never through the bare HTTP adapter: that is what makes a repeated question
 * answer from memory and a down AI service answer at once instead of after the timeout.
 * <p>
 * It gets an application of its own: the integration tests share one, and one that runs before (a sensor report
 * while there is no AI in CI) can leave the circuit open for 30 s, so the CLOSED check failed or passed depending on
 * the order JUnit picked (Avance 33). */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AiWiringIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AiAnalysisClient ai;

    @Autowired
    private CircuitBreakerAiClient breaker;

    @Test
    void theAiClientOfTheApplicationIsTheCachingProxyInFrontOfTheCircuitBreaker() {
        assertThat(ai).isInstanceOf(CachingAiClient.class);
        assertThat(ai).extracting("target").isSameAs(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerAiClient.State.CLOSED);
    }
}
