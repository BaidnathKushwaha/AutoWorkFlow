package com.autoworkflow.dashboard.dto;

/** One point on the Dashboard's "Execution Overview" area chart. */
public record ExecutionOverviewPoint(String label, long count) {}
