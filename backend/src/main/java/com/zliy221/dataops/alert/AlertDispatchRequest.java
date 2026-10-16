package com.zliy221.dataops.alert;

import java.util.List;

public record AlertDispatchRequest(List<Long> ids, String owner, AlertBulkAction action) {}
