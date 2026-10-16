package com.zliy221.dataops.alert;

import java.time.Instant;
import java.util.List;

public record AlertDispatchResult(
        String owner, AlertBulkAction action, int count,
        int changedCount, int unchangedCount,
        int assignedCount, int statusChangedCount,
        int serviceCount, List<String> services,
        String receiptId, Instant executedAt, List<Alert> items
) {}
