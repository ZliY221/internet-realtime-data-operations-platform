package com.zliy221.dataops.alert;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AlertDispatchResult(
        String owner, AlertBulkAction action, int count,
        int changedCount, int unchangedCount,
        String outcome,
        int assignedCount, int statusChangedCount,
        int criticalCount, int warningCount,
        Map<String, Integer> statusBeforeCounts,
        int serviceCount, List<String> services,
        boolean followUpRequired, String reviewPriority, Integer reviewWindowHours,
        String nextAction, List<String> handoffChecklist, int handoffStepCount,
        int estimatedHandoffMinutes, String handoffSummary,
        String receiptId, Instant executedAt, List<Alert> items
) {}
