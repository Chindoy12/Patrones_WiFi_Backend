package com.wifisense.controller;

import com.wifisense.dto.AiModelOverview;
import com.wifisense.service.AiModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Team-only view of the AI model; for end users the route answers 404 like any other team route. */
@RestController
@RequestMapping("/api/ai")
public class AiModelController {

    private final AiModelService service;

    public AiModelController(AiModelService service) {
        this.service = service;
    }

    @GetMapping("/model")
    public AiModelOverview model() {
        return service.overview();
    }
}
