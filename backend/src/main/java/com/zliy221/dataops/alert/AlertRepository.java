package com.zliy221.dataops.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop100ByStatusOrderByCreatedAtDesc(AlertStatus status);
    List<Alert> findTop100ByStatusAndSeverityOrderByCreatedAtDesc(AlertStatus status, AlertSeverity severity);
    List<Alert> findTop100ByStatusAndOwnerOrderByCreatedAtDesc(AlertStatus status, String owner);
    List<Alert> findTop100ByStatusAndSeverityAndOwnerOrderByCreatedAtDesc(AlertStatus status, AlertSeverity severity, String owner);
    List<Alert> findTop100ByStatusAndServiceNameOrderByCreatedAtDesc(AlertStatus status, String serviceName);
    List<Alert> findTop100ByStatusAndSeverityAndServiceNameOrderByCreatedAtDesc(AlertStatus status, AlertSeverity severity, String serviceName);
    List<Alert> findTop100ByStatusAndOwnerAndServiceNameOrderByCreatedAtDesc(AlertStatus status, String owner, String serviceName);
    List<Alert> findTop100ByStatusAndSeverityAndOwnerAndServiceNameOrderByCreatedAtDesc(AlertStatus status, AlertSeverity severity, String owner, String serviceName);
    List<Alert> findTop200ByStatusNotOrderByCreatedAtDesc(AlertStatus status);
    long countByStatus(AlertStatus status);
    long countBySeverityAndStatusNot(AlertSeverity severity, AlertStatus status);
    long countByStatusNot(AlertStatus status);
    long countByServiceNameAndStatusNot(String serviceName, AlertStatus status);
}

