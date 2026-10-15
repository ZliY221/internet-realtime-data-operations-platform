package com.zliy221.dataops.alert;

import java.util.List;

public record AlertBulkRequest(List<Long> ids, AlertBulkAction action) {}
