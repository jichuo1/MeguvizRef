package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

public final class CodeIdentities {
    private CodeIdentities() { }
    /** Hashes immutable artifacts; the owner must synchronize code-set changes and advance CodeScope epochs. */
    public static CodeIdentity hashFiles(Map<String, Path> files, String loadingConfiguration, String ruleVersion) throws IOException {
        if (files.isEmpty() || files.size() > 1024) throw new IllegalArgumentException("artifact count");
        Map<String, String> hashes = new TreeMap<>();
        byte[] buffer = new byte[16384];
        for (Map.Entry<String, Path> file : files.entrySet()) {
            MessageDigest digest = digest();
            try (InputStream input = Files.newInputStream(file.getValue())) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("identity hashing interrupted");
                    digest.update(buffer, 0, count);
                }
            }
            hashes.put(file.getKey(), hex(digest.digest()));
        }
        return CodeIdentity.strong(hashes, loadingConfiguration, ruleVersion);
    }
    static String sha256(byte[] input) { return hex(digest().digest(input)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) { result[i * 2] = alphabet[(bytes[i] & 255) >>> 4]; result[i * 2 + 1] = alphabet[bytes[i] & 15]; }
        return new String(result);
    }
}
