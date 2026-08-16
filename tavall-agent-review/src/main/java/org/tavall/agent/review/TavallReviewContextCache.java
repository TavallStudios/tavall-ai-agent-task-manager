package org.tavall.agent.review;

import org.tavall.abstractcache.cache.enums.CacheDomain;
import org.tavall.abstractcache.cache.enums.CacheSource;
import org.tavall.abstractcache.cache.enums.CacheVersion;
import org.tavall.abstractcache.semantic.SemanticCache;
import org.tavall.abstractcache.semantic.model.SemanticCacheKey;
import org.tavall.abstractcache.semantic.spi.CacheCodec;
import org.tavall.scheduler.CustomScheduler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/** Exact-head review context cache backed by tavall-cache and maintained by tavall-scheduler. */
public final class TavallReviewContextCache implements ReviewContextCache {
    private static final CachedContextCodec CODEC = new CachedContextCodec();

    private final SemanticCache cache;
    private final Duration ttl;
    private final CustomScheduler scheduler;
    private final ScheduledFuture<?> maintenanceTask;

    public TavallReviewContextCache(SemanticCache cache, Duration ttl, CustomScheduler scheduler) {
        this.cache = cache;
        this.ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
        this.scheduler = scheduler;
        long intervalMs = Math.max(1_000L, Math.min(this.ttl.toMillis(), 30_000L));
        this.maintenanceTask = scheduler.runTaskRepeatingAsync(cache::runMaintenanceCycle, intervalMs, intervalMs);
    }

    @Override
    public Optional<ReviewContext> get(ReviewRequest request) {
        return cache.get(key(request), CODEC).map(value -> value.getValue().toContext(request));
    }

    @Override
    public void put(ReviewContext context) {
        cache.put(key(context.request()), CachedContext.from(context), ttl, CODEC);
    }

    @Override
    public void invalidate(ReviewRequest request) {
        cache.invalidate(key(request));
    }

    @Override
    public void close() {
        scheduler.cancelTask(maintenanceTask);
        cache.close();
    }

    private SemanticCacheKey key(ReviewRequest request) {
        return new SemanticCacheKey(request.cacheFingerprint(), CacheDomain.DEBUG, CacheSource.AI_SCANNER, CacheVersion.V1_0, Set.of());
    }

    private record CachedContext(String text, Set<String> areas, List<ReviewEvidence> evidence) {
        private static CachedContext from(ReviewContext context) {
            return new CachedContext(context.canonicalText(), context.inspectedAreas(), context.evidence());
        }

        private ReviewContext toContext(ReviewRequest request) {
            return new ReviewContext(request, text, areas, evidence);
        }
    }

    private static final class CachedContextCodec implements CacheCodec<CachedContext> {
        @Override public String codecId() { return "tavall-review-context-v1"; }

        @Override
        public byte[] encode(CachedContext value) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    writeString(out, value.text());
                    out.writeInt(value.areas().size());
                    for (String area : value.areas()) writeString(out, area);
                    out.writeInt(value.evidence().size());
                    for (ReviewEvidence evidence : value.evidence()) {
                        writeString(out, evidence.kind());
                        writeString(out, evidence.detail());
                        out.writeBoolean(evidence.passed());
                    }
                }
                return bytes.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to encode review context cache entry", exception);
            }
        }

        @Override
        public CachedContext decode(byte[] bytes) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                String text = readString(in);
                int areaCount = in.readInt();
                Set<String> areas = new HashSet<>();
                for (int i = 0; i < areaCount; i++) areas.add(readString(in));
                int evidenceCount = in.readInt();
                List<ReviewEvidence> evidence = new ArrayList<>();
                for (int i = 0; i < evidenceCount; i++) {
                    evidence.add(new ReviewEvidence(readString(in), readString(in), in.readBoolean()));
                }
                return new CachedContext(text, Set.copyOf(areas), List.copyOf(evidence));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to decode review context cache entry", exception);
            }
        }

        private static void writeString(DataOutputStream out, String value) throws IOException {
            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            out.writeInt(data.length);
            out.write(data);
        }

        private static String readString(DataInputStream in) throws IOException {
            int length = in.readInt();
            if (length < 0 || length > 128 * 1024 * 1024) throw new IOException("Invalid review cache string length: " + length);
            return new String(in.readNBytes(length), StandardCharsets.UTF_8);
        }
    }
}
