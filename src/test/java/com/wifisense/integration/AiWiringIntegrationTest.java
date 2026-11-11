package com.wifisense.integration;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.CachingAiClient;
import com.wifisense.analysis.CircuitBreakerAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** In the running application every service that asks the AI gets the caching proxy, which reaches the AI only through
 * the Proxy with the Circuit Breaker, never through the bare HTTP adapter: that is what makes a repeated question
 * answer from memory and a down AI service answer at once instead of after the timeout. */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
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
