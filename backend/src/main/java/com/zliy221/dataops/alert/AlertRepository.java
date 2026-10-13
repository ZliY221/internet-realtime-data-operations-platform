package com.zliy221.dataops.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop100ByStatusOrderByCreatedAtDesc(AlertStatus status);
    List<Alert> findTop100ByStatusAndSeverityOrderByCreatedAtDesc(AlertStatus status, AlertSeverity severity);
    long countByStatus(AlertStatus status);
    long countBySeverityAndStatusNot(AlertSeverity severity, AlertStatus status);
    long countByStatusNot(AlertStatus status);
}

