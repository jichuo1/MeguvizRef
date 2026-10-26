package io.github.jichuo1.meguviz.ref;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import io.github.jichuo1.meguviz.ref.api.evidence.*;
import io.github.jichuo1.meguviz.ref.api.query.*;
import io.github.jichuo1.meguviz.ref.api.result.*;
import io.github.jichuo1.meguviz.ref.runtime.invoke.*;
import io.github.jichuo1.meguviz.ref.runtime.resolve.*;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import io.github.jichuo1.meguviz.ref.spi.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;
import javax.tools.ToolProvider;

/** Dependency-free, executable contracts with handwritten expected descriptors and standard-reflection oracles. */
public final class CoreContractTest {
    private static final List<String> xml = new ArrayList<>();
    private static int count, failures;
    private static Path fixtures, missing;
    private static final ClassLoader LOADER = CoreContractTest.class.getClassLoader();
    private static final CodeIdentity IDENTITY = CodeIdentity.strong(Collections.singletonMap("fixture", "a".repeat(64)), "parent-first-fixture", QuerySpec.RULE_VERSION);
    public static void main(String[] args) throws Exception {
        prepareFixtures();
        descriptorContracts();
        exactAndInvocationContracts();
        structuralContracts();
        lifecycleAndLoaderContracts();
        providerContracts();
        concurrencyContracts();
        if (args.length > 0) {
            Path output = Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"MeguvizRef.CoreContracts\" tests=\"" + count + "\" failures=\"" + failures + "\">" + String.join("", xml) + "</testsuite>");
        }
        System.out.println("TESTS=" + count + " FAILURES=" + failures);
        if (count == 0 || failures != 0) throw new AssertionError("contract failures=" + failures);
    }
    private static void descriptorContracts() {
        for (String invalid : new String[]{"", "[V", "VV", "L;", "Lfoo.bar;", "L/a;", "La//b;", "(I)V", "["}) {
            test("invalid-type-" + invalid, () -> expect(IllegalArgumentException.class, () -> TypeDescriptor.of(invalid)));
        }
        test("array-bound", () -> expect(IllegalArgumentException.class, () -> TypeDescriptor.of("[".repeat(256) + "I")));
        test("descriptor-limit", () -> expect(IllegalArgumentException.class, () -> TypeDescriptor.of("L" + "a".repeat(4096) + ";")));
        test("void-parameter", () -> expect(IllegalArgumentException.class, () -> MemberDescriptor.method("X", "f", "V", "V")));
        test("void-field", () -> expect(IllegalArgumentException.class, () -> MemberDescriptor.field("X", "f", "V")));
        test("constructor-name", () -> expect(IllegalArgumentException.class, () -> MemberDescriptor.create(MemberKind.CONSTRUCTOR, "X", "wrong", "V")));
        test("method-special-name", () -> expect(IllegalArgumentException.class, () -> MemberDescriptor.method("X", "<init>", "V")));
        test("method-special-name-after-newline", () -> expect(IllegalArgumentException.class, () -> MemberDescriptor.method("X", "before\n<invalid>", "V")));
        test("portable-complete-signature", () -> eq("java.lang.String#substring(II)Ljava/lang/String;", MemberDescriptor.method("java.lang.String", "substring", "Ljava/lang/String;", "I", "I").signature()));
        test("return-in-identity", () -> check(!MemberDescriptor.method("X", "f", "I").equals(MemberDescriptor.method("X", "f", "J")), "return included"));
        test("descriptor-list-immutable", () -> expect(UnsupportedOperationException.class, () -> MemberDescriptor.method("X", "f", "V", "I").parameters().clear()));
        test("type-bind-arrays", () -> eq(String[][].class, TypeDescriptor.of("[[Ljava/lang/String;").resolve(null)));
        test("runtime-query-not-portable", () -> check(!QuerySpec.builder(Target.class, MemberKind.METHOD).build().portable(), "runtime Class"));
        test("diagnostic-delimiter-collision-is-not-cache-identity", () -> {
            QuerySpec a = QuerySpec.builder("a:b",MemberKind.METHOD).name("c").build();
            QuerySpec b = QuerySpec.builder("a",MemberKind.METHOD).name("b:c").build();
            eq(a.summary(),b.summary()); check(!a.equals(b),"structural identity");
            Map<QuerySpec,String> cache=new HashMap<>();cache.put(a,"first");cache.put(b,"second");eq(2,cache.size());eq("first",cache.get(a));eq("second",cache.get(b));
        });
        test("exact-query-portable", () -> check(QuerySpec.exact(descriptor("four", "I", "I", "I", "I", "I")).portable(), "literal"));
        test("query-snapshot-arrays", () -> {
            Class<?>[] values = {int.class}; QuerySpec query = QuerySpec.builder(Target.class, MemberKind.METHOD).parameters(values).build();
            values[0] = String.class; Class<?>[] read = query.parameterClasses(); read[0] = long.class; eq(int.class, query.parameterClasses()[0]);
        });
        test("constructors-not-inherited", () -> expect(IllegalArgumentException.class, () -> QuerySpec.builder(Target.class, MemberKind.CONSTRUCTOR).hierarchy(Hierarchy.SUPERCLASSES).build()));
        test("incomplete-exact-rejected", () -> expect(IllegalArgumentException.class, () -> QuerySpec.builder(Target.class, MemberKind.METHOD).selection(Selection.EXACT).build()));
    }
    private static void exactAndInvocationContracts() {
        test("vertical-slice-lookup-invoke-close", () -> {
            CodeScope scope = CodeScope.open(LOADER);
            ResolutionResult result = scope.resolve(QuerySpec.exact(descriptor("four", "I", "I", "I", "I", "I")));
            eq(ResolutionStatus.RESOLVED, result.status()); check(result.receipt().coverage().complete(), "exact coverage");
            MethodPlan plan = scope.bindMethod(result.requireSingle()); eq(10, plan.invoke(new Target(), new Object[]{1, 2, 3, 4}));
            scope.close(); scope.close(); eq(CodeScope.State.CLOSED, scope.state()); expect(StalePlanException.class, () -> plan.invoke(new Target(), new Object[]{1, 2, 3, 4}));
            eq(ResolutionStatus.STALE, scope.resolve(QuerySpec.exact(descriptor("four", "I", "I", "I", "I", "I"))).status());
        });
        test("exact-no-full-schema", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) { scope.resolve(QuerySpec.exact(descriptor("four", "I", "I", "I", "I", "I"))).requireSingle(); eq(0L, scope.diagnostics().get("schemas")); }
        });
        test("exact-missing-unavailable-not-physical-proof", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) { ResolutionResult result = scope.resolve(QuerySpec.exact(descriptor("missing", "I"))); eq(ResolutionStatus.UNAVAILABLE, result.status()); eq("not_found_or_filtered", result.receipt().reason()); }
        });
        test("exact-wrong-return", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.UNAVAILABLE, scope.resolve(QuerySpec.exact(descriptor("four", "J", "I", "I", "I", "I"))).status()); } });
        test("exact-covariant-bridge-return", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                Method bridge = (Method) scope.exportRaw(scope.resolve(QuerySpec.exact(MemberDescriptor.method(Child.class.getName(), "covariant", "Ljava/lang/Object;"))).requireSingle());
                check(bridge.isBridge() && bridge.isSynthetic(), "compiler bridge");
                Method actual = (Method) scope.exportRaw(scope.resolve(QuerySpec.exact(MemberDescriptor.method(Child.class.getName(), "covariant", "Ljava/lang/String;"))).requireSingle());
                check(!actual.isBridge(), "actual return");
            }
        });
        test("field-read-write", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                Target target = new Target(); FieldPlan plan = scope.bindField(scope.resolve(QuerySpec.exact(MemberDescriptor.field(Target.class.getName(), "number", "I"))).requireSingle());
                plan.set(target, (byte) 7); eq(7, plan.get(target)); expect(IllegalArgumentException.class, () -> plan.set(target, 9L));
            }
        });
        test("constructor-call", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { Target target = (Target) scope.bindConstructor(scope.resolve(QuerySpec.exact(MemberDescriptor.constructor(Target.class.getName(), "I"))).requireSingle()).newInstance(new Object[]{23}); eq(23, target.number); } });
        test("constructor-throws-wrapper", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { InvocationTargetException failure = expect(InvocationTargetException.class, () -> scope.bindConstructor(scope.resolve(QuerySpec.exact(MemberDescriptor.constructor(Target.class.getName(), "I"))).requireSingle()).newInstance(new Object[]{-1})); check(failure.getCause() instanceof IllegalArgumentException, "cause"); } });
        test("target-exception-no-retry", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                Target target = new Target(); MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("throwOnce", "V"))).requireSingle());
                InvocationTargetException error = expect(InvocationTargetException.class, () -> plan.invoke(target, new Object[0]));
                check(error.getCause() instanceof IllegalStateException, "original cause"); eq(1, target.calls);
            }
        });
        test("target-null-distinct-from-lookup-failure", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(null, scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("nullable", "Ljava/lang/Object;"))).requireSingle()).invoke(new Target(), new Object[0])); } });
        test("wrong-receiver-arity-type", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("four", "I", "I", "I", "I", "I"))).requireSingle());
                expect(IllegalArgumentException.class, () -> plan.invoke("wrong", new Object[]{1, 2, 3, 4}));
                expect(IllegalArgumentException.class, () -> plan.invoke(new Target(), new Object[]{1}));
                expect(IllegalArgumentException.class, () -> plan.invoke(new Target(), new Object[]{"1", 2, 3, 4}));
            }
        });
        test("reflection-widening-executes", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(3L, scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("wide", "J", "J"))).requireSingle()).invoke(new Target(), new Object[]{3})); } });
        test("preserve-access-default", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("privateValue", "I"))).requireSingle()); expect(IllegalAccessException.class, () -> plan.invoke(new Target(), new Object[0])); } });
        test("explicit-suppress-access", () -> { try (CodeScope scope = CodeScope.open(LOADER, AccessPolicy.TRY_SUPPRESS_JAVA_CHECKS, 128)) { eq(42, scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("privateValue", "I"))).requireSingle()).invoke(new Target(), new Object[0])); } });
        test("access-policy-scopes-isolated", () -> {
            try (CodeScope open = CodeScope.open(LOADER, AccessPolicy.TRY_SUPPRESS_JAVA_CHECKS, 128); CodeScope preserve = CodeScope.open(LOADER)) {
                QuerySpec query = QuerySpec.exact(descriptor("privateValue", "I")); open.bindMethod(open.resolve(query).requireSingle());
                expect(IllegalAccessException.class, () -> preserve.bindMethod(preserve.resolve(query).requireSingle()).invoke(new Target(), new Object[0]));
            }
        });
        test("virtual-dispatch", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq("child", scope.bindMethod(scope.resolve(QuerySpec.exact(MemberDescriptor.method(Parent.class.getName(), "covariant", "Ljava/lang/Object;"))).requireSingle()).invoke(new Child(), new Object[0])); } });
        test("public-java-lookup", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { ResolutionResult result = scope.resolve(QuerySpec.builder(Child.class, MemberKind.METHOD).name("toString").parameters().hierarchy(Hierarchy.JAVA_PUBLIC).build()); eq(Object.class, scope.exportRaw(result.requireSingle()).getDeclaringClass()); } });
    }
    private static void structuralContracts() {
        test("unique-null-overloads-ambiguous", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.AMBIGUOUS, scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("pick").arguments(CompatibilityPolicy.LEGACY_BIL, new Object[]{null}).build()).status()); } });
        test("first-preserves-reflection-order", () -> {
            Method expected = Arrays.stream(Target.class.getDeclaredMethods()).filter(m -> m.getName().equals("pick")).findFirst().orElseThrow();
            try (CodeScope scope = CodeScope.open(LOADER)) { ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("pick").selection(Selection.FIRST).build()); eq(expected, scope.exportRaw(result.requireSingle())); eq(Selection.FIRST, result.receipt().selection()); check(!result.receipt().coverage().complete(), "first not unique proof"); }
        });
        test("all-order-and-count", () -> {
            List<Method> expected = new ArrayList<>(); for (Method m : Target.class.getDeclaredMethods()) if (m.getName().equals("pick")) expected.add(m);
            try (CodeScope scope = CodeScope.open(LOADER)) {
                ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("pick").selection(Selection.ALL).build()); eq(ResolutionStatus.ALL_MATCHES, result.status()); eq(2, result.members().size());
                for (int i = 0; i < expected.size(); i++) eq(expected.get(i), scope.exportRaw(result.members().get(i)));
            }
        });
        test("all-limit-partial", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.PARTIAL, scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("pick").selection(Selection.ALL).resultLimit(1).build()).status()); } });
        test("budget-not-negative-proof", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("noSuchMethod").candidateBudget(1).build()); eq(ResolutionStatus.PARTIAL, result.status()); eq(1, result.receipt().coverage().inspected()); } });
        test("complete-visible-not-found", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("noSuchMethod").build()); eq(ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE, result.status()); check(result.receipt().coverage().complete(), "visible complete"); } });
        test("field-shadowing-unique-ambiguous", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.AMBIGUOUS, scope.resolve(QuerySpec.builder(Child.class, MemberKind.FIELD).name("shadow").hierarchy(Hierarchy.SUPERCLASSES).build()).status()); } });
        test("field-nearest-explicit-first", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(Child.class, scope.exportRaw(scope.resolve(QuerySpec.builder(Child.class, MemberKind.FIELD).name("shadow").hierarchy(Hierarchy.SUPERCLASSES).selection(Selection.FIRST).build()).requireSingle()).getDeclaringClass()); } });
        test("hierarchy-includes-object", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(Object.class, scope.exportRaw(scope.resolve(QuerySpec.builder(Child.class, MemberKind.METHOD).name("hashCode").parameters().hierarchy(Hierarchy.SUPERCLASSES).build()).requireSingle()).getDeclaringClass()); } });
        test("bounded-hierarchy-partial", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.PARTIAL, scope.resolve(QuerySpec.builder(Child.class, MemberKind.METHOD).name("hashCode").hierarchy(Hierarchy.SUPERCLASSES).depthLimit(1).build()).status()); } });
        test("bridge-filter", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { Method method = (Method) scope.exportRaw(scope.resolve(QuerySpec.builder(Child.class, MemberKind.METHOD).name("covariant").bridge(false).build()).requireSingle()); eq(String.class, method.getReturnType()); } });
        test("legacy-no-widening", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { eq(ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE, scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("wide").arguments(CompatibilityPolicy.LEGACY_BIL, 1).build()).status()); } });
        test("java-invocation-widening-query", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("wide").arguments(CompatibilityPolicy.JAVA_INVOCATION, 1).build()).requireSingle(); } });
        Class<?>[] primitives = {boolean.class, byte.class, short.class, char.class, int.class, long.class, float.class, double.class};
        Class<?>[] boxes = {Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Long.class, Float.class, Double.class};
        boolean[][] widening = {{true,false,false,false,false,false,false,false},{false,true,false,false,false,false,false,false},{false,true,true,false,false,false,false,false},{false,false,false,true,false,false,false,false},{false,true,true,true,true,false,false,false},{false,true,true,true,true,true,false,false},{false,true,true,true,true,true,true,false},{false,true,true,true,true,true,true,true}};
        test("primitive-conversion-matrix-64", () -> { for (int p = 0; p < 8; p++) for (int a = 0; a < 8; a++) { eq(widening[p][a], CompatibilityPolicy.JAVA_INVOCATION.accepts(primitives[p], boxes[a])); eq(p == a, CompatibilityPolicy.LEGACY_BIL.accepts(primitives[p], boxes[a])); } });
        test("null-primitive-reference", () -> { check(!CompatibilityPolicy.LEGACY_BIL.accepts(int.class, null), "primitive rejects null"); check(CompatibilityPolicy.LEGACY_BIL.accepts(String.class, null), "reference accepts null"); });
        test("varargs-no-implicit-pack", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                eq(ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE, scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("varargs").arguments(CompatibilityPolicy.JAVA_INVOCATION, "a", "b").build()).status());
                scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("varargs").arguments(CompatibilityPolicy.JAVA_INVOCATION, (Object) new String[]{"a", "b"}).build()).requireSingle();
            }
        });
        for (String name : new String[]{"zero", "one", "four", "eight"}) test("single-parameter-snapshot-" + name, () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                QuerySpec query = QuerySpec.builder(Target.class, MemberKind.METHOD).name(name).selection(Selection.FIRST).build();
                scope.resolve(query).requireSingle(); eq(1L, scope.diagnostics().get("parameterReads"));
                scope.resolve(query).requireSingle(); eq(1L, scope.diagnostics().get("parameterReads"));
            }
        });
        test("name-static-arity-early-exit-no-parameter-read", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("nonexistent").build());
                scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("four").staticOnly(true).build());
                scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("four").parameterCount(3).build());
                eq(0L, scope.diagnostics().get("parameterReads"));
            }
        });
        test("legacy-fixed-oracle-differential", () -> {
            Object[][] cases = {{1,2,3,4},{null,2,3,4},{1L,2,3,4},{1,"2",3,4}};
            for (Object[] arguments : cases) {
                Method expected = oldBilMatch(Target.class, "four", arguments);
                try (CodeScope scope = CodeScope.open(LOADER)) {
                    ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("four").arguments(CompatibilityPolicy.LEGACY_BIL, arguments).selection(Selection.FIRST).build());
                    if (expected == null) eq(ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE, result.status()); else eq(expected, scope.exportRaw(result.requireSingle()));
                }
            }
        });
    }
    private static void lifecycleAndLoaderContracts() {
        test("code-epoch-invalidates-plans", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("zero", "I"))).requireSingle()); scope.advanceCodeEpoch("test code replacement"); expect(StalePlanException.class, () -> plan.invoke(new Target(), new Object[0])); eq(1L, scope.codeEpoch()); eq(0L, scope.diagnostics().get("bindings")); } });
        test("epoch-overflow-new-identity", () -> {
            try (CodeScope scope = CodeScope.open(LOADER)) {
                Field epoch = CodeScope.class.getDeclaredField("codeEpoch"); epoch.setAccessible(true); epoch.setLong(scope, Long.MAX_VALUE);
                String before = scope.identity(); scope.advanceCodeEpoch("overflow fixture"); check(!before.equals(scope.identity()), "new identity"); eq(0L, scope.codeEpoch());
            }
        });
        test("different-scopes-reject-token", () -> { try (CodeScope one = CodeScope.open(LOADER); CodeScope two = CodeScope.open(LOADER)) { ResolvedMember member = one.resolve(QuerySpec.exact(descriptor("zero", "I"))).requireSingle(); expect(StalePlanException.class, () -> two.bindMethod(member)); } });
        test("raw-member-outlives-close", () -> { CodeScope scope = CodeScope.open(LOADER); Method raw = (Method) scope.exportRaw(scope.resolve(QuerySpec.exact(descriptor("zero", "I"))).requireSingle()); scope.close(); eq(0, raw.invoke(new Target())); });
        test("cache-only-complete-success", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { QuerySpec success = QuerySpec.exact(descriptor("zero", "I")); scope.resolve(success); scope.resolve(success); eq(1L, scope.diagnostics().get("cacheHits")); QuerySpec partial = QuerySpec.builder(Target.class, MemberKind.METHOD).name("missing").candidateBudget(1).build(); scope.resolve(partial); scope.resolve(partial); eq(1L, scope.diagnostics().get("cachedQueries")); } });
        test("bounded-live-bindings-explicit-partial", () -> { try (CodeScope scope = CodeScope.open(LOADER, AccessPolicy.PRESERVE_JAVA_ACCESS, 1)) { MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor("zero", "I"))).requireSingle()); eq(ResolutionStatus.PARTIAL, scope.resolve(QuerySpec.exact(descriptor("one", "I", "I"))).status()); eq(0, plan.invoke(new Target(), new Object[0])); eq(1L, scope.diagnostics().get("bindings")); } });
        test("bounded-query-cache-eviction", () -> { try (CodeScope scope = CodeScope.open(LOADER, AccessPolicy.PRESERVE_JAVA_ACCESS, 1)) { scope.resolve(QuerySpec.exact(descriptor("zero", "I"))); scope.resolve(QuerySpec.builder(Target.class, MemberKind.METHOD).name("zero").parameters().build()); eq(1L, scope.diagnostics().get("cachedQueries")); } });
        test("close-releases-owned-graphs", () -> { CodeScope scope = CodeScope.open(LOADER); scope.resolve(QuerySpec.exact(descriptor("zero", "I"))); scope.close(); eq(0L, scope.diagnostics().get("bindings")); eq(0L, scope.diagnostics().get("schemas")); eq(0L, scope.diagnostics().get("cachedQueries")); check(!scope.usesLoader(LOADER), "closed identity"); Field loader = CodeScope.class.getDeclaredField("loader"); loader.setAccessible(true); eq(null, loader.get(scope)); });
        test("MVX-L03-two-loader-identity", () -> {
            try (URLClassLoader a = isolated(fixtures); URLClassLoader b = isolated(fixtures); CodeScope one = CodeScope.open(a); CodeScope two = CodeScope.open(b)) {
                Class<?> ac = Class.forName("fixture.LoaderFixture", false, a), bc = Class.forName("fixture.LoaderFixture", false, b); check(ac != bc, "distinct Class");
                QuerySpec portable = QuerySpec.exact(MemberDescriptor.method("fixture.LoaderFixture", "echo", "Lfixture/LoaderFixture;", "Lfixture/LoaderFixture;"));
                eq(ac, one.exportRaw(one.resolve(portable).requireSingle()).getDeclaringClass()); eq(bc, two.exportRaw(two.resolve(portable).requireSingle()).getDeclaringClass());
                eq(ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE, one.resolve(QuerySpec.builder(ac, MemberKind.METHOD).name("echo").parameters(bc).build()).status());
            }
        });
        test("lookup-does-not-initialize", () -> {
            System.clearProperty("meguviz.fixture.initialized");
            try (URLClassLoader loader = isolated(fixtures); CodeScope scope = CodeScope.open(loader)) {
                MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(MemberDescriptor.method("fixture.LoaderFixture", "value", "Ljava/lang/String;"))).requireSingle());
                eq(null, System.getProperty("meguviz.fixture.initialized")); eq("fixture", plan.invoke(null, new Object[0])); eq("yes", System.getProperty("meguviz.fixture.initialized"));
            } finally { System.clearProperty("meguviz.fixture.initialized"); }
        });
        test("missing-signature-no-false-unique", () -> {
            try (URLClassLoader loader = isolated(missing); CodeScope scope = CodeScope.open(loader)) {
                ResolutionResult structural = scope.resolve(QuerySpec.builder("fixture.MissingSignatureFixture", MemberKind.METHOD).name("available").build());
                eq(ResolutionStatus.PARTIAL, structural.status()); check(!structural.receipt().coverage().complete(), "unknown signature");
                ResolutionResult exact = scope.resolve(QuerySpec.exact(MemberDescriptor.method("fixture.MissingSignatureFixture", "available", "Ljava/lang/String;")));
                check(exact.status() == ResolutionStatus.PARTIAL || exact.status() == ResolutionStatus.RESOLVED, "VM-dependent exact reflection surface");
            }
        });
        test("retained-plan-does-not-retain-loader-after-close", () -> {
            Object[] retained = closedPlan(); WeakReference<?> weak = (WeakReference<?>) retained[0];
            for (int i = 0; i < 30 && weak.get() != null; i++) { System.gc(); Thread.sleep(10); }
            System.out.println("GC_OBSERVATION loaderCollected=" + (weak.get() == null) + " (observation, no immediate-GC guarantee)");
            expect(StalePlanException.class, () -> ((MethodPlan) retained[1]).invoke(null, new Object[0]));
        });
    }
    private static void providerContracts() {
        test("MVX-X01-conflicting-provider", () -> expect(IllegalArgumentException.class, () -> ProviderRegistry.create(Arrays.asList(new Provider("a", "same"), new Provider("b", "same")))));
        test("duplicate-provider-id", () -> expect(IllegalArgumentException.class, () -> ProviderRegistry.create(Arrays.asList(new Provider("a", ""), new Provider("a", "")))));
        test("MVX-V01-unknown-spi-major", () -> { Provider provider = new Provider("new", ""); provider.major = 2; expect(IllegalArgumentException.class, () -> ProviderRegistry.create(Collections.singletonList(provider))); });
        test("provider-dependency-cycle", () -> { Provider a = new Provider("a", ""), b = new Provider("b", ""); a.dependencies = Collections.singletonList("b"); b.dependencies = Collections.singletonList("a"); expect(IllegalArgumentException.class, () -> ProviderRegistry.create(Arrays.asList(a,b))); });
        test("provider-deterministic-install-order", () -> { Provider a = new Provider("a", ""), b = new Provider("b", ""); b.dependencies = Collections.singletonList("a"); try (ProviderRegistry registry = ProviderRegistry.create(Arrays.asList(b,a))) { eq(Arrays.asList("a","b"), registry.installationOrder()); } });
        test("provider-explicit-conflict-owner", () -> { try (ProviderRegistry registry = ProviderRegistry.create(Arrays.asList(new Provider("a","cap"),new Provider("b","cap")), Collections.singletonMap("cap","b"))) { eq(Collections.singletonList("b"), registry.installationOrder()); } });
        test("provider-partial-unique-stays-partial", () -> { Provider provider = new Provider("p", ""); provider.complete = false; try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.PARTIAL, scope.resolve(providerQuery()).status()); } });
        test("provider-wrong-identity-stale", () -> { Provider provider = new Provider("p", ""); provider.fingerprint = "wrong"; try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.STALE, scope.resolve(providerQuery()).status()); } });
        test("provider-runtime-filtered-partial", () -> { Provider provider = new Provider("p", ""); provider.candidates = Arrays.asList(descriptor("zero","I"), descriptor("missing","I")); try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.PARTIAL, scope.resolve(providerQuery()).status()); } });
        test("provider-duplicate-runtime-dedup", () -> { Provider provider = new Provider("p", ""); provider.candidates = Arrays.asList(descriptor("zero","I"),descriptor("zero","I")); try (CodeScope scope = providerScope(provider)) { scope.resolve(providerQuery()).requireSingle(); } });
        test("provider-candidate-outside-domain", () -> { Provider provider = new Provider("p", ""); provider.candidates = Collections.singletonList(MemberDescriptor.method(String.class.getName(),"length","I")); try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.PARTIAL, scope.resolve(providerQuery()).status()); } });
        test("provider-weak-identity-rejected", () -> { try (CodeScope scope = CodeScope.open(LOADER); ProviderRegistry registry = ProviderRegistry.create(Collections.singletonList(new Provider("p","")))) { expect(IllegalArgumentException.class, () -> scope.installProviders(registry, CodeIdentity.unverified("fixture", QuerySpec.RULE_VERSION))); } });
        test("provider-snapshot-owned-and-frozen", () -> { Provider provider = new Provider("p", ""); CodeScope scope = providerScope(provider); scope.resolve(providerQuery()); try (ProviderRegistry registry = ProviderRegistry.create(Collections.singletonList(new Provider("other","")))) { expect(IllegalStateException.class, () -> scope.installProviders(registry, IDENTITY)); } scope.close(); scope.close(); eq(1, provider.closes.get()); });
        test("provider-epoch-requires-new-content-identity", () -> { Provider provider = new Provider("p", ""); try (CodeScope scope = providerScope(provider)) { scope.resolve(providerQuery()).requireSingle(); scope.advanceCodeEpoch("changed code"); eq(ResolutionStatus.UNAVAILABLE, scope.resolve(providerQuery()).status()); } });
        test("MVX-X02-bound-10000-no-candidate-checks", () -> {
            Provider provider = new Provider("p", ""); try (CodeScope scope = providerScope(provider)) {
                MethodPlan plan = scope.bindMethod(scope.resolve(providerQuery()).requireSingle()); long before = scope.diagnostics().get("candidateChecks");
                Target target = new Target(); for (int i = 0; i < 10000; i++) eq(0, plan.invoke(target, new Object[0]));
                eq(before, scope.diagnostics().get("candidateChecks")); eq(1, provider.discoveries.get());
            }
        });
        test("provider-independent-finite-domain-replay", () -> { Provider provider = new Provider("p", ""); try (CodeScope scope = providerScope(provider)) { ResolutionResult result = scope.resolve(providerQuery()); Method expected = Target.class.getDeclaredMethod("zero"); eq(expected, scope.exportRaw(result.requireSingle())); eq(Target.class.getName()+"#zero()I", result.receipt().descriptors().get(0).signature()); } });
        test("provider-complete-claim-does-not-prove-unique", () -> {
            Provider provider = new Provider("p", ""); provider.candidates = Collections.singletonList(descriptor("pick","Ljava/lang/String;","Ljava/lang/String;"));
            try (CodeScope scope = providerScope(provider)) {
                ResolutionResult result = scope.resolve(QuerySpec.builder(Target.class,MemberKind.METHOD).name("pick").provider("p").build());
                eq(ResolutionStatus.PARTIAL,result.status()); eq("provider_coverage_not_proven",result.receipt().reason());
            }
        });
        test("provider-complete-claim-does-not-prove-absence", () -> { Provider provider = new Provider("p", ""); provider.candidates = Collections.emptyList(); try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.PARTIAL,scope.resolve(providerQuery()).status()); } });
        test("provider-java-public-rejects-private", () -> {
            Provider provider = new Provider("p", ""); provider.candidates = Collections.singletonList(descriptor("privateValue","I"));
            try (CodeScope scope = providerScope(provider)) { eq(ResolutionStatus.PARTIAL,scope.resolve(QuerySpec.builder(Target.class,MemberKind.METHOD).name("privateValue").hierarchy(Hierarchy.JAVA_PUBLIC).provider("p").build()).status()); }
        });
        test("provider-explicit-cancellation-is-distinct", () -> { Provider provider = new Provider("p", ""); provider.cancel=true; try (CodeScope scope=providerScope(provider)) { eq(ResolutionStatus.CANCELLED,scope.resolve(providerQuery()).status()); eq(0L,scope.diagnostics().get("cachedQueries")); } });
    }
    private static void concurrencyContracts() {
        test("close-in-flight-target-nonblocking", () -> {
            CodeScope scope = CodeScope.open(LOADER); Blocking target = new Blocking(); MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(MemberDescriptor.method(Blocking.class.getName(),"run","I"))).requireSingle());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Object> result = executor.submit(() -> plan.invoke(target, new Object[0])); await(target.entered);
                scope.close(); eq(CodeScope.State.RETIRING, scope.state()); expect(StalePlanException.class, () -> plan.invoke(target, new Object[0]));
                target.exit.countDown(); eq(1, result.get(3, TimeUnit.SECONDS)); eq(CodeScope.State.CLOSED, scope.state());
            } finally { target.exit.countDown(); scope.close(); executor.shutdownNow(); }
        });
        test("interrupted-query-preserves-flag", () -> { try (CodeScope scope = CodeScope.open(LOADER)) { Thread.currentThread().interrupt(); eq(ResolutionStatus.CANCELLED, scope.resolve(QuerySpec.exact(descriptor("zero","I"))).status()); check(Thread.currentThread().isInterrupted(), "interrupt preserved"); } finally { Thread.interrupted(); } });
        test("single-flight-shared-miss", () -> {
            Provider provider = new Provider("p", ""); provider.entered = new CountDownLatch(1); provider.exit = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            try (CodeScope scope = providerScope(provider)) {
                List<Future<ResolutionResult>> futures = new ArrayList<>(); for (int i = 0; i < 4; i++) futures.add(executor.submit(() -> scope.resolve(providerQuery())));
                await(provider.entered); provider.exit.countDown(); for (Future<ResolutionResult> future : futures) future.get(3, TimeUnit.SECONDS).requireSingle(); eq(1, provider.discoveries.get());
            } finally { provider.exit.countDown(); executor.shutdownNow(); }
        });
        test("single-flight-reentrant-does-not-deadlock", () -> {
            QueryCoordinator<String,Integer> coordinator = new QueryCoordinator<>(4);
            expect(IllegalStateException.class, () -> coordinator.execute("q", () -> { try { return coordinator.execute("q", () -> 2, 100); } catch (InterruptedException | TimeoutException e) { throw new AssertionError(e); } }, 100));
            coordinator.close();
        });
        test("single-flight-waiter-timeout-owner-continues", () -> {
            QueryCoordinator<String,Integer> coordinator = new QueryCoordinator<>(4); CountDownLatch entered = new CountDownLatch(1), exit = new CountDownLatch(1); ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Integer> owner = executor.submit(() -> coordinator.execute("q", () -> { entered.countDown(); blockingAwait(exit); return 7; }, 100)); await(entered);
                expect(TimeoutException.class, () -> coordinator.execute("q", () -> 8, 20)); exit.countDown(); eq(7, owner.get(3,TimeUnit.SECONDS));
            } finally { exit.countDown(); coordinator.close(); executor.shutdownNow(); }
        });
        test("close-completes-waiters-before-provider-returns", () -> {
            Provider provider = new Provider("p", ""); provider.entered = new CountDownLatch(1); provider.exit = new CountDownLatch(1); CodeScope scope = providerScope(provider); ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<ResolutionResult> owner = executor.submit(() -> scope.resolve(providerQuery())); await(provider.entered);
                Future<ResolutionResult> waiter = executor.submit(() -> scope.resolve(providerQuery()));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2); while (scope.diagnostics().get("inFlight") < 2 && System.nanoTime() < deadline) Thread.yield();
                check(scope.diagnostics().get("inFlight") == 2, "waiter entered"); scope.close(); eq(ResolutionStatus.STALE, waiter.get(1,TimeUnit.SECONDS).status()); eq(0,provider.closes.get());
                provider.exit.countDown(); eq(ResolutionStatus.STALE,owner.get(3,TimeUnit.SECONDS).status()); eq(1,provider.closes.get());
            } finally { provider.exit.countDown(); scope.close(); executor.shutdownNow(); }
        });
        test("single-flight-different-keys-parallel", () -> {
            QueryCoordinator<String,Integer> coordinator = new QueryCoordinator<>(4); CountDownLatch both = new CountDownLatch(2), exit = new CountDownLatch(1); ExecutorService executor = Executors.newFixedThreadPool(2);
            try { Future<Integer> a = executor.submit(() -> coordinator.execute("a", () -> { both.countDown(); blockingAwait(exit); return 1; },100)); Future<Integer> b = executor.submit(() -> coordinator.execute("b", () -> { both.countDown(); blockingAwait(exit); return 2; },100)); await(both); exit.countDown(); eq(1,a.get(3,TimeUnit.SECONDS)); eq(2,b.get(3,TimeUnit.SECONDS)); }
            finally { exit.countDown(); coordinator.close(); executor.shutdownNow(); }
        });
        test("single-flight-cross-thread-cycle", () -> {
            QueryCoordinator<String,Integer> coordinator = new QueryCoordinator<>(4); CyclicBarrier barrier = new CyclicBarrier(2); ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> a = executor.submit(() -> coordinator.execute("a", () -> cycle(coordinator, "b", barrier),1000));
                Future<Integer> b = executor.submit(() -> coordinator.execute("b", () -> cycle(coordinator, "a", barrier),1000));
                expect(ExecutionException.class, () -> a.get(3,TimeUnit.SECONDS)); expect(ExecutionException.class, () -> b.get(3,TimeUnit.SECONDS));
            } finally { coordinator.close(); executor.shutdownNow(); }
        });
        test("single-flight-one-waiter-interrupt-independent", () -> {
            QueryCoordinator<String,Integer> coordinator = new QueryCoordinator<>(4); CountDownLatch entered = new CountDownLatch(1), exit = new CountDownLatch(1), waiterStarted = new CountDownLatch(1); ExecutorService executor = Executors.newSingleThreadExecutor(); AtomicBoolean interrupted = new AtomicBoolean();
            try {
                Future<Integer> owner = executor.submit(() -> coordinator.execute("q", () -> { entered.countDown(); blockingAwait(exit); return 9; },1000)); await(entered);
                Thread waiter = new Thread(() -> { waiterStarted.countDown(); try { coordinator.execute("q", () -> 5,1000); } catch (InterruptedException e) { interrupted.set(true); } catch (TimeoutException e) { throw new AssertionError(e); } }); waiter.start(); await(waiterStarted); waiter.interrupt(); waiter.join(2000); check(interrupted.get(), "waiter interrupted"); exit.countDown(); eq(9,owner.get(3,TimeUnit.SECONDS));
            } finally { exit.countDown(); coordinator.close(); executor.shutdownNow(); }
        });
        test("provider-close-failure-preserves-target-exception", () -> {
            Provider provider = new Provider("p", ""); provider.failClose = true; CodeScope scope = providerScope(provider);
            ClosingTarget target = new ClosingTarget(scope);
            MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(MemberDescriptor.method(ClosingTarget.class.getName(),"run","V"))).requireSingle());
            InvocationTargetException failure = expect(InvocationTargetException.class, () -> plan.invoke(target,new Object[0]));
            eq("original target",failure.getCause().getMessage()); eq(IllegalStateException.class.getName(),scope.closeFailureType()); eq(CodeScope.State.CLOSED,scope.state());
        });
    }
    private static int cycle(QueryCoordinator<String,Integer> coordinator, String dependency, CyclicBarrier barrier) {
        try { barrier.await(2,TimeUnit.SECONDS); return coordinator.execute(dependency, () -> 0,1000); } catch (InterruptedException | TimeoutException | BrokenBarrierException e) { throw new AssertionError(e); }
    }
    private static QuerySpec providerQuery() { return QuerySpec.builder(Target.class,MemberKind.METHOD).name("zero").provider("p").build(); }
    private static CodeScope providerScope(Provider provider) { CodeScope scope = CodeScope.open(LOADER); scope.installProviders(ProviderRegistry.create(Collections.singletonList(provider)), IDENTITY); return scope; }
    private static MemberDescriptor descriptor(String name, String returns, String... parameters) { return MemberDescriptor.method(Target.class.getName(), name, returns, parameters); }
    private static void prepareFixtures() throws IOException {
        Path root = Path.of("").toAbsolutePath(); while (!Files.isDirectory(root.resolve("test-fixtures/isolated-src"))) { root = root.getParent(); if (root == null) throw new IOException("test-fixtures not found"); }
        fixtures = Files.createDirectories(root.resolve("ref-core/build/isolated-fixtures/all")); missing = Files.createDirectories(root.resolve("ref-core/build/isolated-fixtures/missing/fixture")).getParent();
        Path sources = root.resolve("test-fixtures/isolated-src/fixture");
        int result = ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",fixtures.toString(),sources.resolve("LoaderFixture.java").toString(),sources.resolve("MissingType.java").toString(),sources.resolve("MissingSignatureFixture.java").toString());
        if (result != 0) throw new IOException("fixture compilation failed: " + result);
        Files.copy(fixtures.resolve("fixture/MissingSignatureFixture.class"), missing.resolve("fixture/MissingSignatureFixture.class"), StandardCopyOption.REPLACE_EXISTING);
        Path completeJar = root.resolve("ref-core/build/isolated-fixtures/complete-fixture.jar");
        fixtureJar(completeJar, fixtures, "fixture/LoaderFixture.class", "fixture/MissingType.class", "fixture/MissingSignatureFixture.class");
        Path missingJar = root.resolve("ref-core/build/isolated-fixtures/missing-signature-fixture.jar");
        fixtureJar(missingJar, missing, "fixture/MissingSignatureFixture.class");
        fixtures = completeJar; missing = missingJar;
    }
    private static void fixtureJar(Path output, Path directory, String... entries) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            for (String entry : entries) { JarEntry item = new JarEntry(entry); item.setTime(0); jar.putNextEntry(item); Files.copy(directory.resolve(entry), jar); jar.closeEntry(); }
        }
    }
    private static URLClassLoader isolated(Path directory) throws MalformedURLException { return new URLClassLoader(new URL[]{directory.toUri().toURL()}, null); }
    private static Object[] closedPlan() throws Exception {
        URLClassLoader loader = isolated(fixtures); WeakReference<ClassLoader> weak = new WeakReference<>(loader); CodeScope scope = CodeScope.open(loader);
        MethodPlan plan = scope.bindMethod(scope.resolve(QuerySpec.exact(MemberDescriptor.method("fixture.LoaderFixture","value","Ljava/lang/String;"))).requireSingle()); scope.close(); loader.close(); return new Object[]{weak,plan};
    }
    // Fixed BIL baseline matcher: boxing + isInstance, no widening; deliberately independent of production policy.
    private static Method oldBilMatch(Class<?> owner, String name, Object[] args) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
            boolean accepted = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> type = method.getParameterTypes()[i]; Object arg = args[i];
                if (arg == null ? type.isPrimitive() : !(type == int.class ? Integer.class : type).isInstance(arg)) { accepted = false; break; }
            }
            if (accepted) return method;
        }
        return null;
    }
    private static void test(String name, Checked test) {
        long start = System.nanoTime(); String error = null; count++;
        try { test.run(); System.out.println("PASS " + name); } catch (Throwable failure) { failures++; error = failure.toString(); System.err.println("FAIL " + name); failure.printStackTrace(); }
        xml.add("<testcase name=\"" + escape(name) + "\" classname=\"CoreContractTest\" time=\"" + (System.nanoTime()-start)/1_000_000_000.0 + "\">" + (error == null ? "" : "<failure message=\"" + escape(error) + "\"/>") + "</testcase>");
    }
    private static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void eq(Object expected, Object actual) { if (!Objects.equals(expected, actual)) throw new AssertionError("expected="+expected+" actual="+actual); }
    private static <T extends Throwable> T expect(Class<T> type, Checked action) throws Exception { try { action.run(); } catch (Throwable failure) { if (type.isInstance(failure)) return type.cast(failure); throw new AssertionError("expected "+type+", got "+failure, failure); } throw new AssertionError("expected "+type.getName()); }
    private static void await(CountDownLatch latch) throws InterruptedException { check(latch.await(3,TimeUnit.SECONDS), "latch timeout"); }
    private static void blockingAwait(CountDownLatch latch) { try { await(latch); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    private interface Checked { void run() throws Exception; }
    public static class Target {
        public int number, calls;
        public Target() { }
        public Target(int number) { if (number < 0) throw new IllegalArgumentException("negative"); this.number = number; }
        public int zero() { return 0; }
        public int one(int a) { return a; }
        public int four(int a,int b,int c,int d) { return a+b+c+d; }
        public int eight(int a,int b,int c,int d,int e,int f,int g,int h) { return a+b+c+d+e+f+g+h; }
        public long wide(long value) { return value; }
        public String pick(String value) { return "string"; }
        public String pick(Number value) { return "number"; }
        public int varargs(String... values) { return values.length; }
        public Object nullable() { return null; }
        public void throwOnce() { calls++; throw new IllegalStateException("target"); }
        private int privateValue() { return 42; }
    }
    public static class Parent { public int shadow; public Object covariant() { return "parent"; } }
    public static final class Child extends Parent { public String shadow; @Override public String covariant() { return "child"; } }
    public static final class Blocking { final CountDownLatch entered = new CountDownLatch(1), exit = new CountDownLatch(1); public int run() { entered.countDown(); blockingAwait(exit); return 1; } }
    public static final class ClosingTarget { private final CodeScope scope; ClosingTarget(CodeScope scope) { this.scope=scope; } public void run() { scope.close(); throw new IllegalArgumentException("original target"); } }
    private static final class Provider implements CandidateProvider {
        final String id, capability; int major=1; List<String> dependencies=Collections.emptyList(); List<MemberDescriptor> candidates=Collections.singletonList(CoreContractTest.descriptor("zero","I")); boolean complete=true; String fingerprint=IDENTITY.fingerprint();
        final AtomicInteger discoveries=new AtomicInteger(),closes=new AtomicInteger(); CountDownLatch entered,exit; boolean failClose, cancel;
        Provider(String id,String capability) { this.id=id; this.capability=capability; }
        public ProviderDescriptor descriptor() { return new ProviderDescriptor(id,"fixture-1",major,0,capability,dependencies); }
        public CandidateBatch discover(CandidateRequest request) throws InterruptedException { discoveries.incrementAndGet(); if (cancel) throw new CancellationException("fixture"); if (entered!=null) entered.countDown(); if (exit!=null) await(exit); return new CandidateBatch(candidates,fingerprint,"finite-handwritten-fixture",complete); }
        public void close() { closes.incrementAndGet(); if (failClose) throw new IllegalStateException("provider close"); }
    }
}
