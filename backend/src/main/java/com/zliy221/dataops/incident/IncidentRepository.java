package com.zliy221.dataops.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.zliy221.dataops.alert.AlertSeverity;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findFirstByServiceNameAndStatusNotOrderByUpdatedAtDesc(String serviceName, IncidentStatus status);
    List<Incident> findTop100ByStatusOrderByUpdatedAtDesc(IncidentStatus status);
    long countByStatus(IncidentStatus status);
    long countByStatusNotAndSeverity(IncidentStatus status, AlertSeverity severity);
}
