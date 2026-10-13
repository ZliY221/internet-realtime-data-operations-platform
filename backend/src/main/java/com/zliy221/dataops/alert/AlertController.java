package com.zliy221.dataops.alert;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) { this.service = service; }

    @GetMapping
    public List<Alert> list(@RequestParam(defaultValue = "OPEN") AlertStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/acknowledge")
    public Alert acknowledge(@PathVariable long id) { return service.acknowledge(id); }

    @PostMapping("/{id}/resolve")
    public Alert resolve(@PathVariable long id) { return service.resolve(id); }
}

