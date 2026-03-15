package cache;

public record CacheEntryEnvelope<T>(T value, long expiresAtMillis) {

  public boolean isExpired(long nowMillis) {
    return expiresAtMillis <= nowMillis;
  }
}
