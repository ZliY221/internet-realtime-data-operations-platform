package com.zliy221.dataops;

import com.jayway.jsonpath.JsonPath;
import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiWorkflowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TrafficMetricRepository metricRepository;

    @BeforeEach
    void clearData() {
        alertRepository.deleteAll();
        metricRepository.deleteAll();
    }

    @Test
    void metricToAlertOperationsWorkflowIsAvailable() throws Exception {
        String metric = """
                {
                  "serviceName": "order-service",
                  "requestCount": 1000,
                  "errorCount": 80,
                  "p95LatencyMs": 920,
                  "collectedAt": "2026-10-14T01:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metric))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("order-service"))
                .andExpect(jsonPath("$.errorRate").value(0.08));

        mockMvc.perform(get("/api/v1/metrics/recent").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestCount").value(1000));

        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number alertId = JsonPath.read(alerts, "$[0].id");

        mockMvc.perform(patch("/api/v1/alerts/{id}/owner", alertId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"data-ops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("data-ops"));

        mockMvc.perform(post("/api/v1/alerts/{id}/acknowledge", alertId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/v1/alerts/{id}/resolve", alertId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(1));

        mockMvc.perform(get("/api/v1/alerts/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].owner").value("未分派"))
                .andExpect(jsonPath("$[0].total").value(1));

        mockMvc.perform(get("/api/v1/dashboard/summary").param("minutes", "1440"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/services").param("minutes", "1440"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidMetricIsRejectedWithoutPersistence() throws Exception {
        String invalidMetric = """
                {
                  "serviceName": "gateway",
                  "requestCount": 10,
                  "errorCount": 11,
                  "p95LatencyMs": 100,
                  "collectedAt": "2026-10-14T01:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMetric))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/metrics/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void alertQueueSupportsAtomicBulkResolution() throws Exception {
        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceName":"gateway","requestCount":1000,"errorCount":90,
                                 "p95LatencyMs":950,"collectedAt":"2026-10-15T01:00:00Z"}
                                """))
                .andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number first = JsonPath.read(alerts, "$[0].id");
        Number second = JsonPath.read(alerts, "$[1].id");

        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + first + "," + second + "],\"action\":\"RESOLVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(2));
    }

    @Test
    void bulkAlertActionsRejectEmptyInputAndDeduplicateIds() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceName":"checkout","requestCount":1000,"errorCount":90,
                                 "p95LatencyMs":950,"collectedAt":"2026-10-15T02:00:00Z"}
                                """))
                .andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(alerts, "$[0].id");
        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "," + id + "],\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void alertQueueCanBeFilteredByService() throws Exception {
        for (String service : List.of("gateway", "checkout")) {
            mockMvc.perform(post("/api/v1/metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceName\":\"" + service
                                    + "\",\"requestCount\":1000,\"errorCount\":90,"
                                    + "\"p95LatencyMs\":950,\"collectedAt\":\"2026-10-15T03:00:00Z\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/alerts")
                        .param("status", "OPEN")
                        .param("serviceName", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceName").value("checkout"));
        mockMvc.perform(get("/api/v1/metrics/recent")
                        .param("limit", "80")
                        .param("serviceName", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("checkout"));
    }
}
