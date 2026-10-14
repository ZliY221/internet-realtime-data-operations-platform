package com.zliy221.dataops.alert;

public record AlertWorkload(
        String owner,
        long total,
        long open,
        long acknowledged,
        long critical,
        long warning
) {}
