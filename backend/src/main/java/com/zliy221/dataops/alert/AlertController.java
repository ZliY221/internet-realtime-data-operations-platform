package com.zliy221.dataops.alert;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) { this.service = service; }

    @GetMapping
    public List<Alert> list(
            @RequestParam(defaultValue = "OPEN") AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String serviceName
    ) {
        return service.list(status, severity, owner, serviceName);
    }

    @GetMapping("/summary")
    public AlertSummary summary() { return service.summary(); }

    @GetMapping("/workloads")
    public List<AlertWorkload> workloads() { return service.workloads(); }

    @PostMapping("/bulk")
    public AlertBulkResult bulk(@RequestBody AlertBulkRequest request) {
        return service.bulk(request.ids(), request.action());
    }

    @PatchMapping("/bulk-owner")
    public AlertBulkOwnerResult bulkAssign(@RequestBody AlertBulkOwnerRequest request) {
        return service.bulkAssign(request.ids(), request.owner());
    }

    @PostMapping("/dispatch")
    public AlertDispatchResult dispatch(@RequestBody AlertDispatchRequest request) {
        return service.dispatch(request.ids(), request.owner(), request.action());
    }

    @PostMapping("/{id}/acknowledge")
    public Alert acknowledge(@PathVariable long id) { return service.acknowledge(id); }

    @PostMapping("/{id}/resolve")
    public Alert resolve(@PathVariable long id) { return service.resolve(id); }

    @PatchMapping("/{id}/owner")
    public Alert assign(@PathVariable long id, @RequestBody AlertOwnerRequest request) {
        return service.assign(id, request.owner());
    }
}

