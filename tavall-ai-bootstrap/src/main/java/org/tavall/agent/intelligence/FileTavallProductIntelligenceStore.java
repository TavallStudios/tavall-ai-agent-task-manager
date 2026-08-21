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
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.LinkedHashSet;
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

        Properties properties = encode(entry);
        Path target = directory.resolve(entryId + FILE_SUFFIX);
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

        List<TavallProductIntelligenceEntry> entries = new ArrayList<>(files.size());
        for (Path file : files) {
            TavallProductIntelligenceEntry entry = decode(file);
            if (!scopedProductId.equals(entry.productId()) || !scopedAgentId.equals(entry.agentId())) {
                throw new IOException("Persisted intelligence scope does not match requested product/agent");
            }
            entries.add(entry);
        }
        entries.sort(Comparator
                .comparing(TavallProductIntelligenceEntry::recordedAt)
                .thenComparing(TavallProductIntelligenceEntry::entryId));
        return List.copyOf(entries);
    }

    private Path directory(String productId, String agentId) {
        return root.resolve(productHash(requireText(productId, "productId"))).resolve(agentId);
    }

    private static Properties encode(TavallProductIntelligenceEntry entry) {
        Properties properties = new Properties();
        properties.setProperty("entryId", entry.entryId());
        properties.setProperty("productId", entry.productId());
        properties.setProperty("agentId", entry.agentId());
        properties.setProperty("category", entry.category());
        properties.setProperty("key", entry.key());
        properties.setProperty("value", entry.value());
        properties.setProperty("rationale", entry.rationale());
        properties.setProperty("disposition", entry.disposition().name());
        properties.setProperty("recordedAt", entry.recordedAt().toString());
        properties.setProperty("evidence.count", Integer.toString(entry.evidenceReferences().size()));

        int index = 0;
        for (String reference : entry.evidenceReferences()) {
            properties.setProperty("evidence." + index++, reference);
        }
        return properties;
    }

    private static TavallProductIntelligenceEntry decode(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        int evidenceCount;
        try {
            evidenceCount = Integer.parseInt(requiredProperty(properties, "evidence.count"));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid evidence.count in " + file, exception);
        }
        if (evidenceCount < 0) {
            throw new IOException("Negative evidence.count in " + file);
        }

        Set<String> evidence = new LinkedHashSet<>();
        for (int index = 0; index < evidenceCount; index++) {
            evidence.add(requiredProperty(properties, "evidence." + index));
        }

        try {
            return new TavallProductIntelligenceEntry(
                    requiredProperty(properties, "entryId"),
                    requiredProperty(properties, "productId"),
                    requiredProperty(properties, "agentId"),
                    requiredProperty(properties, "category"),
                    requiredProperty(properties, "key"),
                    requiredProperty(properties, "value"),
                    requiredProperty(properties, "rationale"),
                    TavallProductIntelligenceDisposition.valueOf(requiredProperty(properties, "disposition")),
                    evidence,
                    Instant.parse(requiredProperty(properties, "recordedAt"))
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid persisted intelligence entry in " + file, exception);
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
}