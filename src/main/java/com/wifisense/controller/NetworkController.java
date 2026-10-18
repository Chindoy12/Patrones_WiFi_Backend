package com.wifisense.controller;

import com.wifisense.dto.NetworkRequest;
import com.wifisense.dto.NetworkResponse;
import com.wifisense.model.NetworkStatus;
import com.wifisense.network.DataSourceType;
import com.wifisense.service.NetworkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/networks")
public class NetworkController {

    private final NetworkService networkService;

    public NetworkController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping
    public List<NetworkResponse> list(@RequestParam(required = false) NetworkStatus status) {
        return networkService.list(status);
    }

    /** What the form offers as data source: the sensor, plus the demo sources when they are enabled. */
    @GetMapping("/sources")
    public List<DataSourceType> sources() {
        return networkService.selectableSources();
    }

    @GetMapping("/{id}")
    public NetworkResponse get(@PathVariable Long id) {
        return networkService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkResponse create(@Valid @RequestBody NetworkRequest request) {
        return networkService.create(request);
    }

    @PutMapping("/{id}")
    public NetworkResponse update(@PathVariable Long id, @Valid @RequestBody NetworkRequest request) {
        return networkService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        networkService.delete(id);
    }
}
