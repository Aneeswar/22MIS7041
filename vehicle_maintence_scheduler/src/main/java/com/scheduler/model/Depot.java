package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Depot(
    @JsonProperty("ID") String ID,
    @JsonProperty("MechanicHours") int MechanicHours
) {}
