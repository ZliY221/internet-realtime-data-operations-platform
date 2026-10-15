package com.zliy221.dataops.metric;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TrafficMetricRepository extends JpaRepository<TrafficMetric, Long> {
    List<TrafficMetric> findAllByOrderByCollectedAtDesc(Pageable pageable);
    List<TrafficMetric> findByServiceNameOrderByCollectedAtDesc(String serviceName, Pageable pageable);

    @Query("select m from TrafficMetric m where m.collectedAt >= :since order by m.collectedAt asc")
    List<TrafficMetric> findSince(@Param("since") Instant since);
}

