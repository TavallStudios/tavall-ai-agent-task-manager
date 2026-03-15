package cache;

public record CacheKeyEnvelope(
    String rawKey,
    CacheDomain domain,
    CacheType type,
    CacheVersion version,
    CacheSource source
) {
}
