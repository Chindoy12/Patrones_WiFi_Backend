package com.wifisense.controller;

import com.wifisense.dto.AiModelOverview;
import com.wifisense.dto.AiTrainingStatus;
import com.wifisense.service.AiTrainingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import com.wifisense.service.AiModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Team-only view of the AI model; for end users the route answers 404 like any other team route. */
@RestController
@RequestMapping("/api/ai")
public class AiModelController {

    private final AiModelService service;
    private final AiTrainingService training;

    public AiModelController(AiModelService service, AiTrainingService training) {
        this.service = service;
        this.training = training;
    }

    @GetMapping("/model")
    public AiModelOverview model() {
        return service.overview();
    }

    /** Real measurements available for training and every training so far. */
    @GetMapping("/training")
    public AiTrainingStatus training() {
        return training.status();
    }

    /** ADMIN (POST): trains now; the new model replaces the one in use only if it is better. */
    @PostMapping("/training")
    public AiTrainingStatus.Version train(Authentication authentication) {
        return training.train(authentication.getName());
    }
}
