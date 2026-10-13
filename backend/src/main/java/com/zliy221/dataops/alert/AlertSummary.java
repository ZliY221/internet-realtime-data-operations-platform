package com.zliy221.dataops.alert;

public record AlertSummary(
        long open,
        long acknowledged,
        long resolved,
        long activeCritical,
        long activeWarning
) {}
