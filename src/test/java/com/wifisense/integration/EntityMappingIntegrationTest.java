package com.wifisense.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every entity of the backend reads from its table of the real schema (the migrations of WiFiSense-database), with
 * every column it maps, including those inherited from a base class such as ObservedDevice or WifiReport. A column
 * renamed on one side only fails here and not in production.
 */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class EntityMappingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private EntityManager entities;

    @Test
    @Transactional
    void everyEntityReadsFromItsTableWithAllItsColumns() {
        List<String> names = entities.getMetamodel().getEntities().stream().map(EntityType::getName).sorted().toList();
        assertThat(names).contains("AgentDevice", "SensorDevice", "AgentReport", "SensorReport");
        for (String name : names) {
            entities.createQuery("SELECT e FROM " + name + " e").setMaxResults(1).getResultList();
        }
    }
}
