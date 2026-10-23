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

    @PostMapping("/{incidentService}/change-correlation")
    public IncidentInsightsService.IncidentChangeRanking rankIncidentChanges(
            @org.springframework.web.bind.annotation.PathVariable String incidentService,
            @RequestBody List<IncidentInsightsService.ChangeCandidate> candidates) {
        return service.rankIncidentChanges(incidentService, candidates);
    }

    @PostMapping("/{failedService}/isolation-plan")
    public IncidentInsightsService.ServiceIsolationPlan planServiceIsolation(
            @org.springframework.web.bind.annotation.PathVariable String failedService,
            @RequestBody List<String> fallbackReadyServices) {
        return service.planServiceIsolation(failedService, fallbackReadyServices);
    }
}
