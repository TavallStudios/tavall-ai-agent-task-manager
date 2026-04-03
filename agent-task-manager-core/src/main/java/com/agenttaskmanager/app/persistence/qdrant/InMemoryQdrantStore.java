package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryQdrantStore {

  private final ConcurrentMap<String, ConcurrentMap<String, StoredPoint>> collections = new ConcurrentHashMap<>();

  public void upsert(String collectionName, String pointId, List<Double> vector, Map<String, Object> payload) {
    collections.computeIfAbsent(collectionName, ignored -> new ConcurrentHashMap<>())
        .put(pointId, new StoredPoint(pointId, List.copyOf(vector), new LinkedHashMap<>(payload)));
  }

  public List<RetrievedSemanticContext> search(
      String collectionName,
      List<Double> queryVector,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    Map<String, StoredPoint> collection = collections.get(collectionName);
    if (collection == null || collection.isEmpty()) {
      return List.of();
    }
    return collection.values().stream()
        .filter(point -> matches(point.payload(), payloadFilter))
        .map(point -> new RetrievedSemanticContext(
            point.id(),
            similarity(queryVector, point.vector()),
            new LinkedHashMap<>(point.payload())
        ))
        .sorted(Comparator.comparingDouble(RetrievedSemanticContext::score).reversed())
        .limit(limit)
        .toList();
  }

  public void deleteByFilter(String collectionName, Map<String, Object> payloadFilter) {
    Map<String, StoredPoint> collection = collections.get(collectionName);
    if (collection == null || collection.isEmpty()) {
      return;
    }
    List<String> pointIds = new ArrayList<>();
    for (StoredPoint point : collection.values()) {
      if (matches(point.payload(), payloadFilter)) {
        pointIds.add(point.id());
      }
    }
    pointIds.forEach(collection::remove);
  }

  public void deleteCollection(String collectionName) {
    collections.remove(collectionName);
  }

  private boolean matches(Map<String, Object> payload, Map<String, Object> payloadFilter) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Object> entry : payloadFilter.entrySet()) {
      if (!Objects.equals(payload.get(entry.getKey()), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private double similarity(List<Double> left, List<Double> right) {
    if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
      return 0.0D;
    }
    int size = Math.min(left.size(), right.size());
    double dotProduct = 0.0D;
    double leftMagnitude = 0.0D;
    double rightMagnitude = 0.0D;
    for (int index = 0; index < size; index++) {
      double leftValue = left.get(index);
      double rightValue = right.get(index);
      dotProduct += leftValue * rightValue;
      leftMagnitude += leftValue * leftValue;
      rightMagnitude += rightValue * rightValue;
    }
    if (leftMagnitude == 0.0D || rightMagnitude == 0.0D) {
      return 0.0D;
    }
    return dotProduct / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
  }

  private record StoredPoint(String id, List<Double> vector, Map<String, Object> payload) {
  }
}
