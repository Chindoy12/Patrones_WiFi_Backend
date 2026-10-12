package com.wifisense.dto;

import com.wifisense.model.HomeNetwork;

import java.time.Instant;

public record HomeNetworkResponse(Long id, String name, String provider, Integer contractedMbps, Instant createdAt) {

    public static HomeNetworkResponse from(HomeNetwork network) {
        return new HomeNetworkResponse(network.getId(), network.getName(), network.getProvider(),
                network.getContractedMbps(), network.getCreatedAt());
    }
}
