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
    private static final String TEMP_FILE_PREFIX = "tpi-";
    private static final String BATCH_FORMAT = "batch-v1";

    private final Path root;

    public FileTavallProductIntelligenceStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public void record(TavallProductIntelligenceEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        String entryId = requirePathIdentifier(entry.entryId(), "entryId");
        String agentId = requirePathIdentifier(entry.agentId(), "agentId");
        Path directory = directory(entry.productId(), agentId);
        Files.createDirectories(directory);

        Properties properties = new Properties();
        encode(properties, "", entry);
        Path target = directory.resolve(entryId + FILE_SUFFIX);
        Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, ".tmp");
        try {
            writeProperties(temporary, properties);
            moveAtomicallyWhenSupported(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void recordAll(List<TavallProductIntelligenceEntry> entries) throws IOException {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        List<TavallProductIntelligenceEntry> batch = List.copyOf(entries);
        TavallProductIntelligenceEntry first = Objects.requireNonNull(batch.getFirst(), "entries entry");
        String productId = requireText(first.productId(), "productId");
        String agentId = requirePathIdentifier(first.agentId(), "agentId");
        Set<String> entryIds = new LinkedHashSet<>();

        for (TavallProductIntelligenceEntry entry : batch) {
            Objects.requireNonNull(entry, "entries entry");
            if (!productId.equals(requireText(entry.productId(), "productId"))) {
                throw new IllegalArgumentException("atomic intelligence batch must use one productId");
            }
            if (!agentId.equals(requirePathIdentifier(entry.agentId(), "agentId"))) {
                throw new IllegalArgumentException("atomic intelligence batch must use one agentId");
            }
            String entryId = requirePathIdentifier(entry.entryId(), "entryId");
            if (!entryIds.add(entryId)) {
                throw new IllegalArgumentException("atomic intelligence batch contains duplicate entryId: " + entryId);
            }
        }

        Path directory = directory(productId, agentId);
        Files.createDirectories(directory);
        Properties properties = new Properties();
        properties.setProperty("format", BATCH_FORMAT);
        properties.setProperty("entry.count", Integer.toString(batch.size()));
        for (int index = 0; index < batch.size(); index++) {
            encode(properties, "entry." + index + ".", batch.get(index));
        }

        Path target = directory.resolve(batchFileName(productId, agentId, entryIds));
        Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, ".tmp");
        try {
            writeProperties(temporary, properties);
            moveBatchAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException {
        String scopedProductId = requireText(productId, "productId");
        String scopedAgentId = requirePathIdentifier(agentId, "agentId");
        Path directory = directory(scopedProductId, scopedAgentId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        Map<String, TavallProductIntelligenceEntry> entriesById = new LinkedHashMap<>();
        for (Path file : files) {
            for (TavallProductIntelligenceEntry entry : decodeAll(file)) {
                if (!scopedProductId.equals(entry.productId()) || !scopedAgentId.equals(entry.agentId())) {
                    throw new IOException("Persisted intelligence scope does not match requested product/agent");
                }
                TavallProductIntelligenceEntry previous = entriesById.get(entry.entryId());
                if (previous == null || entry.recordedAt().isAfter(previous.recordedAt())) {
                    entriesById.put(entry.entryId(), entry);
                } else if (entry.recordedAt().equals(previous.recordedAt()) && !entry.equals(previous)) {
                    throw new IOException("Conflicting intelligence entries share entryId: " + entry.entryId());
                }
            }
        }

        List<TavallProductIntelligenceEntry> entries = new ArrayList<>(entriesById.values());
        entries.sort(Comparator
                .comparing(TavallProductIntelligenceEntry::recordedAt)
                .thenComparing(TavallProductIntelligenceEntry::entryId));
        return List.copyOf(entries);
    }

    private Path directory(String productId, String agentId) {
        return root.resolve(productHash(requireText(productId, "productId"))).resolve(agentId);
    }

    private static void writeProperties(Path file, Properties properties) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
    }

    private static void encode(Properties properties, String prefix, TavallProductIntelligenceEntry entry) {
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

        int index = 0;
        for (String reference : entry.evidenceReferences()) {
            properties.setProperty(prefix + "evidence." + index++, reference);
        }
    }

    private static List<TavallProductIntelligenceEntry> decodeAll(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        if (!BATCH_FORMAT.equals(properties.getProperty("format"))) {
            return List.of(decode(properties, "", file));
        }

        int entryCount = nonNegativeInteger(properties, "entry.count", file);
        if (entryCount == 0) {
            throw new IOException("Empty intelligence batch in " + file);
        }
        List<TavallProductIntelligenceEntry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            entries.add(decode(properties, "entry." + index + ".", file));
        }
        return List.copyOf(entries);
    }

    private static TavallProductIntelligenceEntry decode(Properties properties, String prefix, Path file) throws IOException {
        int evidenceCount = nonNegativeInteger(properties, prefix + "evidence.count", file);
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

    private static int nonNegativeInteger(Properties properties, String key, Path file) throws IOException {
        try {
            int value = Integer.parseInt(requiredProperty(properties, key));
            if (value < 0) {
                throw new IOException("Negative " + key + " in " + file);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid " + key + " in " + file, exception);
        }
    }

    private static String requiredProperty(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing persisted intelligence property: " + key);
        }
        return value;
    }

    private static void moveAtomicallyWhenSupported(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveBatchAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic filesystem move is required for multi-entry intelligence decisions", exception);
        }
    }

    private static String batchFileName(String productId, String agentId, Set<String> entryIds) {
        String material = productId + "\u0000" + agentId + "\u0000" + String.join("\u0000", entryIds.stream().sorted().toList());
        return "batch-" + sha256(material) + FILE_SUFFIX;
    }

    private static String productHash(String productId) {
        return sha256(productId);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
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
}
