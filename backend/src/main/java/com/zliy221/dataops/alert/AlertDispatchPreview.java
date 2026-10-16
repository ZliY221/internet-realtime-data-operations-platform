package com.zliy221.dataops.alert;

public record AlertDispatchPreview(
        int count, int assignedCount, int statusChangedCount,
        int criticalCount, int warningCount, boolean requiresConfirmation
) {}
