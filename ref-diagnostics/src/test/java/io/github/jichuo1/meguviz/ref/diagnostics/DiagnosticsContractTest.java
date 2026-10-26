package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind;
import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import io.github.jichuo1.meguviz.ref.api.query.AccessPolicy;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DiagnosticsContractTest {
    public static final class Target {
        public String echo(String value) { return value; }
        public int number = 7;
    }
    private static final MemberDescriptor ECHO = MemberDescriptor.method(Target.class.getName(), "echo", "Ljava/lang/String;", "Ljava/lang/String;");
    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final CodeIdentity IDENTITY = CodeIdentity.strong(Collections.singletonMap("fixture.jar", DIGEST), "parent-first:fixture", QuerySpec.RULE_VERSION);
    private static final AccessPolicy POLICY = AccessPolicy.PRESERVE_JAVA_ACCESS;
    private static int tests;
    private static final StringBuilder cases = new StringBuilder();
    private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--child")) { child(Paths.get(args[1]), args[2]); return; }
        Path output = Paths.get(args.length == 0 ? "ref-diagnostics/build/test-results/contracts/TEST-diagnostics.xml" : args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        try {
            run("identity_is_order_independent_but_binds_configuration", () -> {
                Map<String, String> first = new LinkedHashMap<>(); first.put("b", DIGEST); first.put("a", DIGEST);
                Map<String, String> second = new LinkedHashMap<>(); second.put("a", DIGEST); second.put("b", DIGEST);
                CodeIdentity one = CodeIdentity.strong(first, "config", QuerySpec.RULE_VERSION);
                check(one.matchesStrong(CodeIdentity.strong(second, "config", QuerySpec.RULE_VERSION)), "order");
                check(!one.matchesStrong(CodeIdentity.strong(second, "other", QuerySpec.RULE_VERSION)), "config");
                reject(() -> CodeIdentity.strong(Collections.singletonMap("x", "mtime:123"), "c", "v"));
            });
            run("weak_identity_never_trusted", () -> {
                CodeIdentity weak = CodeIdentity.weak(Collections.singletonMap("fixture.jar", "version1"), "config", QuerySpec.RULE_VERSION);
                check(!weak.matchesStrong(weak), "weak self match");
                reject(() -> new StoredResolution(weak, ECHO, POLICY, 0, 0));
                reject(() -> new StoredResolution(CodeIdentity.unverified("x", QuerySpec.RULE_VERSION), ECHO, POLICY, 0, 0));
            });
            run("fingerprint_has_structured_field_boundaries", () -> {
                StoredResolution first = new StoredResolution(IDENTITY, MemberDescriptor.method("a:b", "c", "V"), POLICY, 0, 0);
                StoredResolution second = new StoredResolution(IDENTITY, MemberDescriptor.method("a", "b:c", "V"), POLICY, 0, 0);
                check(!first.queryFingerprint().equals(second.queryFingerprint()), "field boundaries");
            });
            run("envelope_roundtrip_and_no_runtime_objects", () -> {
                byte[] bytes = ReceiptJson.encode(record());
                StoredResolution decoded = ReceiptJson.decode(bytes);
                check(decoded.descriptor().equals(ECHO), "descriptor");
                check(decoded.identity().matchesStrong(IDENTITY), "identity");
                String json = new String(bytes, StandardCharsets.UTF_8);
                check(!json.contains("scopeId") && !json.contains("epoch") && !json.contains("receiver"), "runtime values");
            });
            run("unknown_major_minor_duplicate_truncated_and_tampered", () -> {
                String valid = new String(ReceiptJson.encode(record()), StandardCharsets.UTF_8);
                reject(() -> ReceiptJson.decode(valid.replace("\"schema_major\":1", "\"schema_major\":2").getBytes(StandardCharsets.UTF_8)));
                reject(() -> ReceiptJson.decode(valid.replace("\"schema_minor\":0", "\"schema_minor\":1").getBytes(StandardCharsets.UTF_8)));
                reject(() -> ReceiptJson.decode(valid.replace("\"echo\"", "\"missing\"").getBytes(StandardCharsets.UTF_8)));
                reject(() -> ReceiptJson.decode(valid.substring(0, valid.length() - 2).getBytes(StandardCharsets.UTF_8)));
                reject(() -> BoundedJson.decode("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)));
            });
            run("malformed_utf8_depth_size_and_numbers_rejected", () -> {
                reject(() -> BoundedJson.decode(new byte[]{(byte) 0xc3, 0x28}));
                reject(() -> BoundedJson.decode(new byte[65537]));
                reject(() -> BoundedJson.decode("[[[[[[[[[[[[[[[[[[0]]]]]]]]]]]]]]]]]]".getBytes(StandardCharsets.UTF_8)));
                for (String bad : Arrays.asList("01", "1.2", "-", "[1,]", "{}false", "9223372036854775808"))
                    reject(() -> BoundedJson.decode(bad.getBytes(StandardCharsets.UTF_8)));
                check(BoundedJson.decode("{\"a\":\"line\\n\\u4e2d\"}".getBytes(StandardCharsets.UTF_8)) instanceof Map, "escaping");
            });
            run("memory_store_defensive_copy_and_capacity", () -> {
                MemoryReceiptStore store = new MemoryReceiptStore(1); byte[] original = {1, 2};
                store.write("one", original); original[0] = 9;
                byte[] returned = store.read("one").get(); check(returned[0] == 1, "write copy");
                returned[0] = 9; check(store.read("one").get()[0] == 1, "read copy");
                store.write("two", new byte[]{3}); check(!store.read("one").isPresent(), "capacity");
            });
            run("independent_finite_universe_rejects_omission", () -> {
                check(IndependentVerifier.verifyFiniteUniverse(Target.class, MemberKind.METHOD, Collections.singletonList(ECHO)) == IndependentVerifier.Status.VERIFIED, "universe");
                check(IndependentVerifier.verifyFiniteUniverse(Target.class, MemberKind.METHOD, Collections.emptyList()) == IndependentVerifier.Status.MISMATCH, "omitted candidate");
                check(IndependentVerifier.verify(Target.class.getClassLoader(), ECHO, java.lang.reflect.Modifier.STATIC, 0) == IndependentVerifier.Status.MISMATCH, "wrong static");
            });
            run("persistent_hint_rebind_and_identity_policy_rejection", () -> {
                MemoryReceiptStore store = new MemoryReceiptStore(2); store.write("echo", ReceiptJson.encode(record()));
                try (CodeScope scope = CodeScope.open(Target.class.getClassLoader())) {
                    PersistentResolutions.LoadResult result = PersistentResolutions.restore(store, "echo", IDENTITY, POLICY, Target.class.getClassLoader(), scope);
                    check(result.status() == PersistentResolutions.Status.REBOUND, "rebound");
                    Object value = scope.bindMethod(result.resolution().get().requireSingle()).invoke(new Target(), new Object[]{"real-call"});
                    check(value.equals("real-call"), "invoked");
                    CodeIdentity changed = CodeIdentity.strong(Collections.singletonMap("other.jar", DIGEST), "parent-first:fixture", QuerySpec.RULE_VERSION);
                    check(PersistentResolutions.restore(store, "echo", changed, POLICY, Target.class.getClassLoader(), scope).status() == PersistentResolutions.Status.STALE_IDENTITY, "stale");
                    check(PersistentResolutions.restore(store, "missing", IDENTITY, POLICY, Target.class.getClassLoader(), scope).status() == PersistentResolutions.Status.MISS, "miss");
                    store.write("bad", new byte[]{'{'});
                    check(PersistentResolutions.restore(store, "bad", IDENTITY, POLICY, Target.class.getClassLoader(), scope).status() == PersistentResolutions.Status.INVALID_RECORD, "bad");
                }
            });
            run("file_store_atomic_replace_capacity_and_interruption", () -> {
                Path directory = Files.createTempDirectory(output.toAbsolutePath().getParent(), "file-store-");
                FileReceiptStore store = new FileReceiptStore(directory, 1);
                store.write("../../outside", ReceiptJson.encode(record()));
                store.write("../../outside", ReceiptJson.encode(record()));
                check(store.read("../../outside").isPresent(), "hashed path");
                try { store.write("second", new byte[]{1}); throw new AssertionError("capacity accepted"); } catch (IOException expected) { }
                Thread.currentThread().interrupt();
                try {
                    try { store.write("../../outside", new byte[]{1}); throw new AssertionError("interruption ignored"); }
                    catch (java.io.InterruptedIOException expected) { check(Thread.currentThread().isInterrupted(), "interrupt retained"); }
                } finally { Thread.interrupted(); }
                ReceiptJson.decode(store.read("../../outside").get());
                try (java.util.stream.Stream<Path> files = Files.list(directory)) { check(files.noneMatch(p -> p.toString().endsWith(".tmp")), "temporary leak"); }
            });
            run("restore_binds_actual_scope_policy_and_loader", () -> {
                MemoryReceiptStore store = new MemoryReceiptStore(1); store.write("echo", ReceiptJson.encode(record()));
                try (CodeScope scope = CodeScope.open(Target.class.getClassLoader(), AccessPolicy.TRY_SUPPRESS_JAVA_CHECKS, 8)) {
                    check(PersistentResolutions.restore(store, "echo", IDENTITY, POLICY, Target.class.getClassLoader(), scope).status()
                            == PersistentResolutions.Status.POLICY_MISMATCH, "actual scope policy");
                }
                CodeScope scope = CodeScope.open(Target.class.getClassLoader());
                check(PersistentResolutions.restore(store, "echo", IDENTITY, POLICY, null, scope).status()
                        == PersistentResolutions.Status.STALE_IDENTITY, "wrong loader");
                scope.close();
                check(PersistentResolutions.restore(store, "echo", IDENTITY, POLICY, Target.class.getClassLoader(), scope).status()
                        == PersistentResolutions.Status.STALE_IDENTITY, "closed scope");
            });
            run("hash_content_and_restore_in_separate_process", () -> {
                Path directory = Files.createTempDirectory(output.toAbsolutePath().getParent(), "process-store-");
                Path artifact = directory.resolve("code.bin"); Files.write(artifact, new byte[]{1, 2, 3});
                CodeIdentity original = CodeIdentities.hashFiles(Collections.singletonMap("code", artifact), "config", QuerySpec.RULE_VERSION);
                Files.write(artifact, new byte[]{1, 2, 4});
                check(!original.matchesStrong(CodeIdentities.hashFiles(Collections.singletonMap("code", artifact), "config", QuerySpec.RULE_VERSION)), "content changes");
                new FileReceiptStore(directory, 2).write("echo", ReceiptJson.encode(record()));
                String javaCommand = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                Process child = new ProcessBuilder(javaCommand, "-cp", System.getProperty("java.class.path"), DiagnosticsContractTest.class.getName(),
                        "--child", directory.toString(), "echo").redirectErrorStream(true).redirectOutput(directory.resolve("child.log").toFile()).start();
                if (!child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { child.destroyForcibly(); throw new AssertionError("child timeout"); }
                check(child.exitValue() == 0, "child exit " + child.exitValue());
            });
            Files.write(output, ("<testsuite name=\"DiagnosticsContractTest\" tests=\"" + tests + "\" failures=\"0\" errors=\"0\" skipped=\"0\">" + cases + "</testsuite>\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("DiagnosticsContractTest: " + tests + " tests passed");
        } catch (Throwable failure) {
            Files.write(output, ("<testsuite name=\"DiagnosticsContractTest\" tests=\"" + (tests + 1) + "\" failures=\"1\"><testcase name=\"failure\"><failure>" + escape(failure.toString()) + "</failure></testcase>" + cases + "</testsuite>").getBytes(StandardCharsets.UTF_8));
            throw failure;
        }
    }
    private static void child(Path directory, String key) throws Exception {
        try (CodeScope scope = CodeScope.open(Target.class.getClassLoader())) {
            PersistentResolutions.LoadResult loaded = PersistentResolutions.restore(new FileReceiptStore(directory, 2), key, IDENTITY, POLICY, Target.class.getClassLoader(), scope);
            check(loaded.status() == PersistentResolutions.Status.REBOUND, "child rebound");
            check(scope.bindMethod(loaded.resolution().get().requireSingle()).invoke(new Target(), new Object[]{"child"}).equals("child"), "child invoke");
            System.out.println("separate-process exact descriptor rebound and invoked");
        }
    }
    private static StoredResolution record() { return new StoredResolution(IDENTITY, ECHO, POLICY, java.lang.reflect.Modifier.PUBLIC, java.lang.reflect.Modifier.STATIC); }
    private static void run(String name, Checked action) throws Exception { action.run(); tests++; cases.append("<testcase name=\"").append(name).append("\"/>"); }
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    private static void reject(Checked action) throws Exception { try { action.run(); throw new AssertionError("expected rejection"); } catch (IllegalArgumentException expected) { } }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"); }
}
