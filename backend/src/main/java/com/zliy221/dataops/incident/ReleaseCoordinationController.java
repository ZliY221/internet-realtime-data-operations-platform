package com.zliy221.dataops.incident;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v3/incidents")
public class ReleaseCoordinationController {
    private final IncidentInsightsService service;

    public ReleaseCoordinationController(IncidentInsightsService service) {
        this.service = service;
    }

    @PostMapping("/release-window-coordination")
    public IncidentInsightsService.ReleaseWindowCoordination coordinateReleaseWindows(
            @RequestBody List<IncidentInsightsService.ReleaseWindowRequest> requests) {
        return service.coordinateReleaseWindows(requests);
    }

    @PostMapping("/coordinated-rollback-plan")
    public IncidentInsightsService.CoordinatedRollbackPlan planCoordinatedRollback(
            @RequestBody List<IncidentInsightsService.RollbackServiceRequest> requests) {
        return service.planCoordinatedRollback(requests);
    }
}
