package com.zliy221.dataops.alert;

import java.util.List;

public record AlertBulkOwnerResult(String owner, int count, List<Alert> items) {}
