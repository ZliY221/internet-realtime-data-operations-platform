package com.zliy221.dataops.alert;

import java.util.List;

public record AlertBulkOwnerRequest(List<Long> ids, String owner) {}
