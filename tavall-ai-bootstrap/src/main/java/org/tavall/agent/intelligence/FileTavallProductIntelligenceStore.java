package org.tavall.agent.intelligence;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Host-rooted durable product-intelligence store.
 *
 * <p>The store never discovers a workspace or persistence location. Its root is supplied by a
 * trusted caller. Arbitrary product ids are hashed before they participate in a path, while agent
 * and entry ids are restricted to path-safe stable identifiers.</p>
 */
public final class FileTavallProductIntelligenceStore implements TavallProductIntelligenceStore {
    private static final Pattern PATH_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String FILE_SUFFIX = ".properties";
    private static final String SNAPSHOT_FILE = "intelligence.snapshot.properties";
    private static final String SNAPSHOT_VERSION = "1";
    private static final String TEMP_FILE_PREFIX = "tpi-";

    private final Path root;
    private final SnapshotCommitter snapshotCommitter;

    public FileTavallProductIntelligenceStore(Path root) {
        this(root, FileTavallProductIntelligenceStore::commitSnapshot);
    }

    FileTavallProductIntelligenceStore(Path root, SnapshotCommitter snapshotCommitter) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.snapshotCommitter = Objects.requireNonNull(snapshotCommitter, "snapshotCommitter");
    }

    @Override
    public void recordBatch(List<TavallProductIntelligenceEntry> entries) throws IOException {
        List<TavallProductIntelligenceEntry> batch = entries == null ? List.of() : List.copyOf(entries);
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        TavallProductIntelligenceEntry first = Objects.requireNonNull(batch.getFirst(), "entries must not contain null");
        String productId = requireText(first.productId(), "productId");
        String agentId = requirePathIdentifier(first.agentId(), "agentId");

        for (TavallProductIntelligenceEntry entry : batch) {
            Objects.requireNonNull(entry, "entries must not contain null");
            requirePathIdentifier(entry.entryId(), "entryId");
            String scopedAgentId = requirePathIdentifier(entry.agentId(), "agentId");
            if (!productId.equals(requireText(entry.productId(), "productId")) || !agentId.equals(scopedAgentId)) {
                throw new IllegalArgumentException("All batch entries must share one productId and agentId");
            }
        }

        Path directory = directory(productId, agentId);
        Files.createDirectories(directory);

        Map<String, TavallProductIntelligenceEntry> merged = new LinkedHashMap<>();
        for (TavallProductIntelligenceEntry existing : load(productId, agentId)) {
            merged.put(existing.entryId(), existing);
        }
        for (TavallProductIntelligenceEntry entry : batch) {
            merged.put(entry.entryId(), entry);
        }

        snapshotCommitter.commit(directory, directory.resolve(SNAPSHOT_FILE), encodeSnapshot(merged.values()));
    }

    @Override
    public List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException {
        String scopedProductId = requireText(productId, "productId");
        String scopedAgentId = requirePathIdentifier(agentId, "agentId");
        Path directory = directory(scopedProductId, scopedAgentId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        Path snapshot = directory.resolve(SNAPSHOT_FILE);
        List<TavallProductIntelligenceEntry> entries = Files.isRegularFile(snapshot)
                ? decodeSnapshot(snapshot)
                : loadLegacyEntries(directory);

        for (TavallProductIntelligenceEntry entry : entries) {
            if (!scopedProductId.equals(entry.productId()) || !scopedAgentId.equals(entry.agentId())) {
                throw new IOException("Persisted intelligence scope does not match requested product/agent");
            }
        }
        entries.sort(Comparator
                .comparing(TavallProductIntelligenceEntry::recordedAt)
                .thenComparing(TavallProductIntelligenceEntry::entryId));
        return List.copyOf(entries);
    }

    private List<TavallProductIntelligenceEntry> loadLegacyEntries(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .filter(path -> !path.getFileName().toString().equals(SNAPSHOT_FILE))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        List<TavallProductIntelligenceEntry> entries = new ArrayList<>(files.size());
        for (Path file : files) {
            entries.add(decodeLegacyEntry(file));
        }
        return entries;
    }

    private Path directory(String productId, String agentId) {
        return root.resolve(productHash(requireText(productId, "productId"))).resolve(agentId);
    }

    private static Properties encodeSnapshot(Collection<TavallProductIntelligenceEntry> entries) {
        Properties properties = new Properties();
        properties.setProperty("snapshot.version", SNAPSHOT_VERSION);
        properties.setProperty("entry.count", Integer.toString(entries.size()));
        int index = 0;
        for (TavallProductIntelligenceEntry entry : entries) {
            encodeEntry(properties, "entry." + index++ + ".", entry);
        }
        return properties;
    }

    private static void encodeEntry(Properties properties, String prefix, TavallProductIntelligenceEntry entry) {
        properties.setProperty(prefix + "entryId", entry.entryId());
        properties.setProperty(prefix + "productId", entry.productId());
        properties.setProperty(prefix + "agentId", entry.agentId());
        properties.setProperty(prefix + "category", entry.category());
        properties.setProperty(prefix + "key", entry.key());
        properties.setProperty(prefix + "value", entry.value());
        properties.setProperty(prefix + "rationale", entry.rationale());
        properties.setProperty(prefix + "disposition", entry.disposition().name());
        properties.setProperty(prefix + "recordedAt", entry.recordedAt().toString());
        properties.setProperty(prefix + "evidence.count", Integer.toString(entry.evidenceReferences().size()));

        int evidenceIndex = 0;
        for (String reference : entry.evidenceReferences()) {
            properties.setProperty(prefix + "evidence." + evidenceIndex++, reference);
        }
    }

    private static List<TavallProductIntelligenceEntry> decodeSnapshot(Path file) throws IOException {
        Properties properties = loadProperties(file);
        if (!SNAPSHOT_VERSION.equals(requiredProperty(properties, "snapshot.version"))) {
            throw new IOException("Unsupported product-intelligence snapshot version in " + file);
        }

        int entryCount = parseNonNegativeInt(properties, "entry.count", file);
        List<TavallProductIntelligenceEntry> entries = new ArrayList<>(entryCount);
        Set<String> entryIds = new LinkedHashSet<>();
        for (int index = 0; index < entryCount; index++) {
            TavallProductIntelligenceEntry entry = decodeEntry(properties, "entry." + index + ".", file);
            if (!entryIds.add(entry.entryId())) {
                throw new IOException("Duplicate entryId in product-intelligence snapshot: " + entry.entryId());
            }
            entries.add(entry);
        }
        return entries;
    }

    private static TavallProductIntelligenceEntry decodeLegacyEntry(Path file) throws IOException {
        return decodeEntry(loadProperties(file), "", file);
    }

    private static TavallProductIntelligenceEntry decodeEntry(Properties properties, String prefix, Path file) throws IOException {
        int evidenceCount = parseNonNegativeInt(properties, prefix + "evidence.count", file);
        Set<String> evidence = new LinkedHashSet<>();
        for (int index = 0; index < evidenceCount; index++) {
            evidence.add(requiredProperty(properties, prefix + "evidence." + index));
        }

        try {
            return new TavallProductIntelligenceEntry(
                    requiredProperty(properties, prefix + "entryId"),
                    requiredProperty(properties, prefix + "productId"),
                    requiredProperty(properties, prefix + "agentId"),
                    requiredProperty(properties, prefix + "category"),
                    requiredProperty(properties, prefix + "key"),
                    requiredProperty(properties, prefix + "value"),
                    requiredProperty(properties, prefix + "rationale"),
                    TavallProductIntelligenceDisposition.valueOf(requiredProperty(properties, prefix + "disposition")),
                    evidence,
                    Instant.parse(requiredProperty(properties, prefix + "recordedAt"))
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid persisted intelligence entry in " + file, exception);
        }
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static int parseNonNegativeInt(Properties properties, String key, Path file) throws IOException {
        int value;
        try {
            value = Integer.parseInt(requiredProperty(properties, key));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid " + key + " in " + file, exception);
        }
        if (value < 0) {
            throw new IOException("Negative " + key + " in " + file);
        }
        return value;
    }

    private static String requiredProperty(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing persisted intelligence property: " + key);
        }
        return value;
    }

    private static void commitSnapshot(Path directory, Path target, Properties properties) throws IOException {
        Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
            moveAtomicallyWhenSupported(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomicallyWhenSupported(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String productHash(String productId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(productId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static String requirePathIdentifier(String value, String fieldName) {
        String identifier = requireText(value, fieldName);
        if (!PATH_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(fieldName + " must match " + PATH_IDENTIFIER.pattern());
        }
        if (identifier.equals(".") || identifier.equals("..")) {
            throw new IllegalArgumentException(fieldName + " must not be a dot path component");
        }
        return identifier;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @FunctionalInterface
    interface SnapshotCommitter {
        void commit(Path directory, Path target, Properties properties) throws IOException;
    }
}
