package com.scheduler.model;

import java.util.List;

public record ScheduleResult(
    int totalImpact,
    List<String> selectedTaskIDs
) {}
