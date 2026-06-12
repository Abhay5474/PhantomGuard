package com.phantomguard.controlplane.telemetry;

import com.phantomguard.controlplane.config.MongoConfig;
import com.phantomguard.controlplane.domain.ChildProfileDocument;
import com.phantomguard.controlplane.domain.DnsQueryLogDocument;
import com.phantomguard.controlplane.repository.ChildProfileRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Read-side analytics over the time-series audit collection, used by the
 * dashboard for initial hydration (the live feed then streams deltas).
 */
@Validated
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryQueryController {

    private final MongoTemplate mongoTemplate;
    private final ChildProfileRepository profileRepository;

    public TelemetryQueryController(MongoTemplate mongoTemplate,
                                    ChildProfileRepository profileRepository) {
        this.mongoTemplate = mongoTemplate;
        this.profileRepository = profileRepository;
    }

    @GetMapping("/recent")
    public List<Map<String, Object>> recent(
            @RequestParam @NotBlank String parentId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        List<String> clientIds = clientIdsOf(parentId);
        if (clientIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("clientId").in(clientIds))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        Map<String, String> childNames = childNamesOf(parentId);
        return mongoTemplate.find(query, DnsQueryLogDocument.class, MongoConfig.QUERY_LOG_COLLECTION)
                .stream()
                .map(d -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("clientId", d.getClientId());
                    m.put("childName", childNames.getOrDefault(d.getClientId(), "Unknown"));
                    m.put("domain", d.getDomain());
                    m.put("queryType", d.getQueryType());
                    m.put("status", d.getStatus());
                    m.put("blockReason", d.getBlockReason());
                    m.put("latencyMicros", d.getLatencyMicros());
                    m.put("timestamp", d.getTimestamp() == null ? null : d.getTimestamp().toString());
                    return m;
                })
                .toList();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam @NotBlank String parentId,
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        List<String> clientIds = clientIdsOf(parentId);
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        if (clientIds.isEmpty()) {
            return Map.of("totalAllowed", 0L, "totalBlocked", 0L,
                    "topBlockedDomains", List.of(), "perHour", List.of());
        }

        Criteria scope = Criteria.where("clientId").in(clientIds).and("timestamp").gte(since);

        List<Document> statusCounts = aggregate(Aggregation.newAggregation(
                Aggregation.match(scope),
                Aggregation.group("status").count().as("count")));

        long allowed = 0;
        long blocked = 0;
        for (Document doc : statusCounts) {
            String status = doc.getString("_id");
            long count = ((Number) doc.get("count")).longValue();
            if ("BLOCKED".equals(status)) {
                blocked = count;
            } else {
                allowed += count;
            }
        }

        List<Document> topBlocked = aggregate(Aggregation.newAggregation(
                Aggregation.match(scope.and("status").is("BLOCKED")),
                Aggregation.group("domain").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(10)));

        List<Document> perHour = aggregate(Aggregation.newAggregation(
                Aggregation.match(scope),
                Aggregation.project("status")
                        .andExpression("dateToString('%Y-%m-%dT%H:00', timestamp)").as("hour"),
                Aggregation.group("hour", "status").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id.hour")));

        return Map.of(
                "totalAllowed", allowed,
                "totalBlocked", blocked,
                "topBlockedDomains", topBlocked.stream()
                        .map(d -> Map.of("domain", d.getString("_id"),
                                "count", ((Number) d.get("count")).longValue()))
                        .toList(),
                "perHour", perHour.stream()
                        .map(d -> {
                            Document id = (Document) d.get("_id");
                            return Map.of(
                                    "hour", id.getString("hour"),
                                    "status", id.getString("status"),
                                    "count", ((Number) d.get("count")).longValue());
                        })
                        .toList());
    }

    private List<Document> aggregate(Aggregation aggregation) {
        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, MongoConfig.QUERY_LOG_COLLECTION, Document.class);
        return results.getMappedResults();
    }

    private List<String> clientIdsOf(String parentId) {
        return profileRepository.findByParentIdOrderByCreatedAtAsc(parentId).stream()
                .map(ChildProfileDocument::getClientId)
                .toList();
    }

    private Map<String, String> childNamesOf(String parentId) {
        return profileRepository.findByParentIdOrderByCreatedAtAsc(parentId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ChildProfileDocument::getClientId,
                        ChildProfileDocument::getChildName,
                        (a, b) -> a));
    }
}
