package com.zliy221.dataops.alert;

import java.util.List;

public record AlertDispatchResult(
        String owner, AlertBulkAction action, int count,
        int assignedCount, int statusChangedCount, List<Alert> items
) {}
