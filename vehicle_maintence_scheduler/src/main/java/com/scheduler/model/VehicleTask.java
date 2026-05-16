package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VehicleTask(
    @JsonProperty("TaskID") String TaskID,
    int duration,
    int operationalImpact
) {}
