package com.zliy221.dataops.alert;

import java.util.List;

public record AlertBulkResult(AlertBulkAction action, int count, List<Alert> items) {}
