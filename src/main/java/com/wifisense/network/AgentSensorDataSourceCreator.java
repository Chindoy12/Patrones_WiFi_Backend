package com.wifisense.network;

import org.springframework.stereotype.Component;

@Component
public class AgentSensorDataSourceCreator extends DataSourceCreator {

    @Override
    public DataSourceType type() {
        return DataSourceType.AGENT;
    }

    @Override
    protected NetworkDataSource createDataSource() {
        return new AgentSensorDataSource();
    }
}
