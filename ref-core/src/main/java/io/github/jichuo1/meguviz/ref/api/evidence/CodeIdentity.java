package io.github.jichuo1.meguviz.ref.api.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** Portable content identity. Callers must cover the entire effective code set and loading configuration. */
public final class CodeIdentity {
    public enum Strength { STRONG, WEAK, UNVERIFIED }
    private final Strength strength;
    private final SortedMap<String, String> artifacts;
    private final String loadingConfiguration;
    private final String ruleVersion;
    private final String fingerprint;

    private CodeIdentity(Strength strength, Map<String, String> artifacts,
                         String loadingConfiguration, String ruleVersion) {
        this.strength = Objects.requireNonNull(strength);
        this.loadingConfiguration = bounded(loadingConfiguration, "loadingConfiguration");
        this.ruleVersion = bounded(ruleVersion, "ruleVersion");
        if (artifacts.size() > 1024 || (strength == Strength.STRONG && artifacts.isEmpty()))
            throw new IllegalArgumentException("Invalid artifact count");
        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : artifacts.entrySet()) {
            String key = bounded(entry.getKey(), "artifact name");
            String value = bounded(entry.getValue(), "artifact identity");
            if (strength == Strength.STRONG && !value.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Strong artifact identities require lowercase SHA-256");
            copy.put(key, value);
        }
        this.artifacts = Collections.unmodifiableSortedMap(copy);
        StringBuilder canonical = new StringBuilder();
        append(canonical, strength.name());
        append(canonical, loadingConfiguration);
        append(canonical, ruleVersion);
        for (Map.Entry<String, String> entry : copy.entrySet()) {
            append(canonical, entry.getKey());
            append(canonical, entry.getValue());
        }
        this.fingerprint = sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Digests are supplied by a trusted content hasher; this factory does not read any file. */
    public static CodeIdentity strong(Map<String, String> artifacts, String loadingConfiguration, String ruleVersion) {
        return new CodeIdentity(Strength.STRONG, artifacts, loadingConfiguration, ruleVersion);
    }
    public static CodeIdentity weak(Map<String, String> hints, String loadingConfiguration, String ruleVersion) {
        return new CodeIdentity(Strength.WEAK, hints, loadingConfiguration, ruleVersion);
    }
    public static CodeIdentity unverified(String loadingConfiguration, String ruleVersion) {
        return new CodeIdentity(Strength.UNVERIFIED, Collections.emptyMap(), loadingConfiguration, ruleVersion);
    }
    public Strength strength() { return strength; }
    public SortedMap<String, String> artifacts() { return artifacts; }
    public String loadingConfiguration() { return loadingConfiguration; }
    public String ruleVersion() { return ruleVersion; }
    public String fingerprint() { return fingerprint; }
    public boolean matchesStrong(CodeIdentity current) {
        return current != null && strength == Strength.STRONG && current.strength == Strength.STRONG
                && fingerprint.equals(current.fingerprint);
    }
    private static String bounded(String text, String name) {
        Objects.requireNonNull(text, name);
        if (text.isEmpty() || text.length() > 4096) throw new IllegalArgumentException(name + " size");
        return text;
    }
    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }
    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
