package example.meguviz;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import com.highcapable.kavaref.KavaRef;
import com.highcapable.kavaref.condition.MethodCondition;
import com.highcapable.kavaref.resolver.MethodResolver;
import dalvik.system.InMemoryDexClassLoader;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind;
import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.api.query.Selection;
import io.github.jichuo1.meguviz.ref.api.result.ResolutionResult;
import io.github.jichuo1.meguviz.ref.api.result.ResolutionStatus;
import io.github.jichuo1.meguviz.ref.runtime.invoke.MethodPlan;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import io.github.jichuo1.meguviz.ref.spi.CandidateBatch;
import io.github.jichuo1.meguviz.ref.spi.CandidateProvider;
import io.github.jichuo1.meguviz.ref.spi.CandidateRequest;
import io.github.jichuo1.meguviz.ref.spi.ProviderDescriptor;
import io.github.jichuo1.meguviz.ref.spi.ProviderRegistry;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** On-device measurements only. No timing values are manufactured on the host. */
public final class BenchmarkInstrumentation extends Instrumentation {
    private static final int WARMUP = 2000;
    private static final int SAMPLES = 40;
    private static final Object[] ARGUMENTS = {1, 2, 3, 4};
    private static volatile int sink;
    private int rows;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runMeasurements();
            result.putString("stream", "\nMVX_BENCHMARK_DONE\t" + rows + "\n");
            result.putInt("meguviz_benchmark_rows", rows);
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nMVX_BENCHMARK_FAILED\t" + failure + "\n");
            result.putInt("meguviz_benchmark_rows", rows);
            finish(0, result);
        }
    }

    private void runMeasurements() throws Exception {
        Class<Fixture> owner = Fixture.class;
        Fixture fixture = new Fixture();
        Map<String, String> content = codeContent();
        String fixtureHash = hashFile(getContext().getApplicationInfo().sourceDir);
        CodeIdentity identity = CodeIdentity.strong(content, "instrumentation-loader:target-and-test-apks", QuerySpec.RULE_VERSION);
        List<Method> oldMembers = new ArrayList<>();
        for (MethodResolver<Fixture> method : KavaRef.resolveClass(owner).method(new MethodCondition<Fixture>())) oldMembers.add(method.getSelf());
        MemberDescriptor descriptor = MemberDescriptor.method(owner.getName(), "target", "I", "I", "I", "I", "I");
        QuerySpec exact = QuerySpec.exact(descriptor);
        try (CodeScope scope = CodeScope.open(owner.getClassLoader()); CodeScope extended = CodeScope.open(owner.getClassLoader())) {
            List<Method> fresh = rawMethods(scope, QuerySpec.builder(owner, MemberKind.METHOD).selection(Selection.ALL).build());
            List<MemberDescriptor> candidates = new ArrayList<>();
            for (Method method : fresh) candidates.add(MemberDescriptor.from(method));
            CandidateProvider provider = new CandidateProvider() {
                public ProviderDescriptor descriptor() { return new ProviderDescriptor("fixture", "1", 1, 0, "", Collections.emptyList()); }
                public CandidateBatch discover(CandidateRequest request) { return new CandidateBatch(candidates, identity.fingerprint(), "benchmark fixture visible declared methods", true); }
                public void close() { }
            };
            extended.installProviders(ProviderRegistry.create(Collections.singletonList(provider)), identity);
            List<Method> provided = rawMethods(extended, QuerySpec.builder(owner, MemberKind.METHOD).provider("fixture").selection(Selection.ALL).build());
            require(signatures(oldMembers).equals(signatures(fresh)), "old/new candidate domains differ");
            require(signatures(oldMembers).equals(signatures(provided)), "provider candidate domain differs");
            Method raw = match(oldMembers, true);
            MethodPlan plan = scope.bindMethod(scope.resolve(exact).requireSingle());
            require(Integer.valueOf(10).equals(raw.invoke(fixture, ARGUMENTS)), "baseline target behavior");
            require(Integer.valueOf(10).equals(plan.invoke(fixture, ARGUMENTS)), "core target behavior");
            emitHeader(fixtureHash, identity.fingerprint());
            LinkedHashMap<String, Operation> groups = new LinkedHashMap<>();
            groups.put("A", () -> match(oldMembers, false).getName().length());
            groups.put("B", () -> match(oldMembers, true).getName().length());
            groups.put("C", () -> match(fresh, false).getName().length());
            groups.put("D", () -> match(fresh, true).getName().length());
            groups.put("E", () -> match(provided, true).getName().length());
            measure("warm-compatible-lookup-cached-collection", groups, 1000);
            groups.clear();
            groups.put("B", () -> (Integer) raw.invoke(fixture, ARGUMENTS));
            groups.put("D", () -> (Integer) plan.invoke(fixture, ARGUMENTS));
            measure("bound-invoke", groups, 1000);
            groups.clear();
            groups.put("C", () -> {
                try (CodeScope cold = CodeScope.open(owner.getClassLoader())) { return cold.resolve(exact).requireSingle().descriptor().name().length(); }
            });
            measure("cold-library-exact-query", groups, 50);
            groups.clear();
            groups.put("D", () -> scope.resolve(exact).requireSingle().descriptor().name().length());
            measure("warm-exact-query", groups, 1000);
            coldClassMeasurements();
        }
    }

    private void measure(String scenario, LinkedHashMap<String, Operation> groups, int operations) throws Exception {
        List<Map.Entry<String, Operation>> entries = new ArrayList<>(groups.entrySet());
        for (Map.Entry<String, Operation> entry : entries) for (int i = 0; i < WARMUP; i++) sink = entry.getValue().run();
        for (int sample = 0; sample < SAMPLES; sample++) {
            for (int offset = 0; offset < entries.size(); offset++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("benchmark cancelled");
                Map.Entry<String, Operation> entry = entries.get((sample + offset) % entries.size());
                long allocationBefore = allocatedBytes();
                long start = System.nanoTime();
                for (int i = 0; i < operations; i++) sink = entry.getValue().run();
                long elapsed = System.nanoTime() - start;
                long allocationAfter = allocatedBytes();
                sample(scenario, entry.getKey(), "ns", operations, WARMUP, sample, (double) elapsed / operations);
                if (allocationBefore >= 0 && allocationAfter >= allocationBefore) {
                    sample(scenario, entry.getKey(), "bytes", operations, WARMUP, sample, (double) (allocationAfter - allocationBefore) / operations);
                }
            }
        }
    }

    private void coldClassMeasurements() throws Exception {
        byte[] dex;
        try (InputStream input = getContext().getAssets().open("classes.dex"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            dex = output.toByteArray();
        }
        QuerySpec query = QuerySpec.exact(MemberDescriptor.method("fixture.SameName", "read", "I"));
        Set<Class<?>> identities = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            ClassLoader loader = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), getTargetContext().getClassLoader());
            try (CodeScope scope = CodeScope.open(loader)) {
                long start = System.nanoTime();
                var member = scope.resolve(query).requireSingle();
                long elapsed = System.nanoTime() - start;
                require(identities.add(scope.exportRaw(member).getDeclaringClass()), "cold-class loader delegated to an existing class");
                sample("cold-class-first-query", "C", "ns", 1, 0, i, elapsed);
            }
        }
    }

    private void emitHeader(String fixtureHash, String codeFingerprint) throws Exception {
        JSONObject header = new JSONObject();
        header.put("schema_version", 1);
        header.put("platform", "android");
        header.put("android_api", Build.VERSION.SDK_INT);
        header.put("target_sdk", getTargetContext().getApplicationInfo().targetSdkVersion);
        header.put("host_target_sdk", getTargetContext().getApplicationInfo().targetSdkVersion);
        header.put("abi", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
        header.put("device_or_host", Build.MANUFACTURER + " " + Build.MODEL);
        header.put("os_version", Build.VERSION.RELEASE);
        header.put("runtime_version", System.getProperty("java.vm.version", "unknown"));
        header.put("compilation_state", "ART default install/JIT; no forced compilation; target release/R8; benchmark test APK");
        header.put("thermal_state", "unknown/unmeasured; groups rotated; no CPU pinning or speedup claim");
        header.put("fixture_sha256", fixtureHash);
        header.put("code_fingerprint", codeFingerprint);
        header.put("operations_per_sample", 1000);
        header.put("warmup_count", WARMUP);
        header.put("sample_count", SAMPLES);
        header.put("old_baseline", "KavaRef 1.1.1; test APK only");
        header.put("allocation_metric", "process-wide art.gc.bytes-allocated delta; may include background/statistics overhead; omitted when unavailable");
        JSONObject scenarios = new JSONObject();
        scenarios.put("warm-compatible-lookup-cached-collection", scenario(1000, WARMUP, SAMPLES, "A", "B", "C", "D", "E"));
        scenarios.put("bound-invoke", scenario(1000, WARMUP, SAMPLES, "B", "D"));
        scenarios.put("cold-library-exact-query", scenario(50, WARMUP, SAMPLES, "C"));
        scenarios.put("warm-exact-query", scenario(1000, WARMUP, SAMPLES, "D"));
        scenarios.put("cold-class-first-query", scenario(1, 0, 10, "C"));
        header.put("scenarios", scenarios);
        line("MVX_BENCHMARK_HEADER\t" + header);
    }

    private static JSONObject scenario(int operations, int warmup, int samples, String... groups) throws Exception {
        JSONObject value = new JSONObject();
        value.put("operations_per_sample", operations); value.put("warmup_count", warmup); value.put("sample_count", samples);
        value.put("groups", new JSONArray(Arrays.asList(groups))); return value;
    }
    private void sample(String scenario, String group, String unit, int operations, int warmup, int sample, double value) {
        if (!Double.isFinite(value) || value < 0) throw new AssertionError("invalid measured sample");
        line("MVX_BENCHMARK_SAMPLE\t" + scenario + "\t" + group + "\t" + unit + "\t" + operations + "\t" + warmup + "\t" + sample + "\t" + value); rows++;
    }
    private void line(String line) { Bundle event = new Bundle(); event.putString("stream", "\n" + line + "\n"); sendStatus(0, event); }
    private static long allocatedBytes() {
        String stat = Debug.getRuntimeStat("art.gc.bytes-allocated");
        if (stat == null) return -1;
        try { return Long.parseLong(stat); } catch (NumberFormatException unsupported) { return -1; }
    }
    private static List<Method> rawMethods(CodeScope scope, QuerySpec query) throws IllegalAccessException {
        ResolutionResult result = scope.resolve(query);
        require(result.status() == ResolutionStatus.ALL_MATCHES && result.receipt().coverage().complete(), "complete candidate collection required");
        List<Method> methods = new ArrayList<>();
        for (var member : result.members()) methods.add((Method) scope.exportRaw(member));
        return methods;
    }
    private static Set<String> signatures(List<Method> methods) {
        Set<String> values = new HashSet<>(); for (Method method : methods) values.add(method.toGenericString()); return values;
    }
    private static Method match(List<Method> methods, boolean snapshot) {
        for (Method method : methods) {
            if (!method.getName().equals("target") || Modifier.isStatic(method.getModifiers())) continue;
            if (snapshot) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 4) continue;
                boolean valid = true; for (int i = 0; i < types.length; i++) if (types[i] != int.class || !(ARGUMENTS[i] instanceof Integer)) { valid = false; break; }
                if (valid) return method;
            } else {
                if (method.getParameterTypes().length != 4) continue;
                int length = method.getParameterTypes().length;
                boolean valid = true; for (int i = 0; i < length; i++) if (method.getParameterTypes()[i] != int.class || !(ARGUMENTS[i] instanceof Integer)) { valid = false; break; }
                if (valid) return method;
            }
        }
        throw new AssertionError("matching target missing");
    }
    private Map<String, String> codeContent() throws Exception {
        Map<String, String> identities = new LinkedHashMap<>();
        addPaths(identities, "target", getTargetContext().getApplicationInfo());
        addPaths(identities, "test", getContext().getApplicationInfo());
        return identities;
    }
    private static void addPaths(Map<String, String> target, String role, ApplicationInfo info) throws Exception {
        target.put(role + ":base", hashFile(info.sourceDir));
        if (info.splitSourceDirs != null) for (int i = 0; i < info.splitSourceDirs.length; i++) target.put(role + ":split:" + i, hashFile(info.splitSourceDirs[i]));
    }
    private static String hashFile(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(path)) { byte[] bytes = new byte[32768]; int n; while ((n = input.read(bytes)) != -1) digest.update(bytes, 0, n); }
        StringBuilder value = new StringBuilder(); for (byte b : digest.digest()) { int unsigned = b & 255; if (unsigned < 16) value.append('0'); value.append(Integer.toHexString(unsigned)); } return value.toString();
    }
    private static void require(boolean valid, String reason) { if (!valid) throw new AssertionError(reason); }
    private interface Operation { int run() throws Exception; }
    public static final class Fixture {
        public int target(int a, int b, int c, int d) { return a + b + c + d; }
        public int other(String value) { return value.length(); }
    }
}
