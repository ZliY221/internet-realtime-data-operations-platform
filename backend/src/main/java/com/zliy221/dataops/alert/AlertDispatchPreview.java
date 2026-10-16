package com.zliy221.dataops.alert;

import java.util.List;

public record AlertDispatchPreview(
        int count, int assignedCount, int statusChangedCount,
        int criticalCount, int warningCount, int serviceCount,
        List<String> services, boolean requiresConfirmation
) {}
