package io.github.jichuo1.meguviz.ref.runtime.resolve;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import io.github.jichuo1.meguviz.ref.api.query.*;
import io.github.jichuo1.meguviz.ref.api.result.*;
import io.github.jichuo1.meguviz.ref.spi.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Public reflection only. Candidate enumeration order is preserved for explicit FIRST. */
public final class ReflectionResolver {
    private final int schemaCapacity;
    private final Map<Member, MethodSchema> schemas = new LinkedHashMap<>(16, .75f, true);
    private final AtomicLong parameterReads = new AtomicLong(), candidateChecks = new AtomicLong(), lookups = new AtomicLong();
    public ReflectionResolver(int schemaCapacity) { this.schemaCapacity = schemaCapacity; }
    public long parameterReads() { return parameterReads.get(); }
    public long candidateChecks() { return candidateChecks.get(); }
    public long lookups() { return lookups.get(); }
    public synchronized int schemaSize() { return schemas.size(); }
    public synchronized void clear() { schemas.clear(); }
    private synchronized MethodSchema schema(Member member) {
        MethodSchema cached = schemas.get(member);
        if (cached != null) return cached;
        MethodSchema result = new MethodSchema(member);
        if (!(member instanceof Field)) parameterReads.incrementAndGet();
        if (schemaCapacity > 0) {
            schemas.put(member, result);
            if (schemas.size() > schemaCapacity) schemas.remove(schemas.keySet().iterator().next());
        }
        return result;
    }
    public Outcome resolve(QuerySpec query, ClassLoader loader, ProviderRegistry registry, String codeFingerprint) {
        lookups.incrementAndGet();
        if (Thread.currentThread().isInterrupted()) return failure(ResolutionStatus.CANCELLED, query, "interrupted", null);
        try {
            Class<?> owner = query.runtimeOwner() == null ? Class.forName(query.owner(), false, loader) : query.runtimeOwner();
            if (query.providerId() != null) return provider(query, owner, loader, registry, codeFingerprint);
            if (query.exactDescriptor() != null) return exact(query, owner, loader);
            return structural(query, owner);
        } catch (ClassNotFoundException e) { return failure(ResolutionStatus.UNAVAILABLE, query, "owner_type_unavailable", e); }
        catch (LinkageError e) { return failure(ResolutionStatus.PARTIAL, query, "signature_linkage_failure", e); }
        catch (SecurityException e) { return failure(ResolutionStatus.ACCESS_DENIED, query, "query_access_denied", e); }
    }
    private Outcome exact(QuerySpec query, Class<?> owner, ClassLoader loader) throws ClassNotFoundException {
        MemberDescriptor d = query.exactDescriptor();
        Class<?>[] parameters = new Class<?>[d.parameters().size()];
        for (int i = 0; i < parameters.length; i++) parameters[i] = d.parameters().get(i).resolve(loader);
        Class<?> type = d.type().resolve(loader);
        try {
            Member member;
            if (d.kind() == MemberKind.METHOD) {
                Method method = owner.getDeclaredMethod(d.name(), parameters);
                if (method.getReturnType() != type) {
                    // A covariant bridge can share name and arguments. Search only the declared domain.
                    int inspected = 0;
                    for (Method candidate : owner.getDeclaredMethods()) {
                        if (++inspected > query.candidateBudget()) return failure(ResolutionStatus.PARTIAL, query, "candidate_budget", null);
                        if (candidate.getName().equals(d.name()) && candidate.getReturnType() == type && Arrays.equals(candidate.getParameterTypes(), parameters)) return Outcome.single(candidate, d, query, inspected);
                    }
                    return failure(ResolutionStatus.UNAVAILABLE, query, "not_found_or_filtered", null);
                }
                member = method;
            } else if (d.kind() == MemberKind.FIELD) {
                Field field = owner.getDeclaredField(d.name());
                if (field.getType() != type) return failure(ResolutionStatus.UNAVAILABLE, query, "field_type_mismatch", null);
                member = field;
            } else member = owner.getDeclaredConstructor(parameters);
            return Outcome.single(member, d, query, 1);
        } catch (NoSuchMethodException | NoSuchFieldException e) { return failure(ResolutionStatus.UNAVAILABLE, query, "not_found_or_filtered", e); }
    }
    private Outcome structural(QuerySpec query, Class<?> owner) {
        List<Entry> matches = new ArrayList<>();
        Set<Member> visited = new HashSet<>();
        int inspected = 0, depth = 0;
        boolean complete = true;
        String reason = "complete_visible_universe";
        Throwable cause = null;
        Class<?>[] parameters = query.parameterClasses(), arguments = query.argumentClasses();
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (++depth > query.depthLimit()) { complete = false; reason = "hierarchy_budget"; break; }
            Member[] members;
            try { members = members(current, query); }
            catch (LinkageError e) { complete = false; reason = "signature_linkage_failure"; cause = e; break; }
            catch (SecurityException e) { complete = false; reason = "query_access_denied"; cause = e; break; }
            for (Member member : members) {
                if (Thread.currentThread().isInterrupted()) return outcome(ResolutionStatus.CANCELLED, query, matches, false, inspected, "interrupted", null);
                if (!visited.add(member)) continue;
                if (inspected >= query.candidateBudget()) return select(query, matches, false, inspected, "candidate_budget", cause);
                inspected++; candidateChecks.incrementAndGet();
                try {
                    if (!cheapMatches(member, query)) continue;
                    MethodSchema schema = schema(member);
                    if (!matches(schema, query, parameters, arguments)) continue;
                    matches.add(new Entry(member, schema.descriptor()));
                } catch (LinkageError e) { complete = false; reason = "candidate_signature_unknown"; cause = e; continue; }
                if (query.selection() == Selection.FIRST) return select(query, matches, false, inspected, complete ? "first_in_reflection_order" : reason, cause);
                if (query.selection() == Selection.UNIQUE && matches.size() == 2) return select(query, matches, false, inspected, "multiple_matches", cause);
                if (query.selection() == Selection.ALL && matches.size() == query.resultLimit()) {
                    // Reaching a limit is conservatively partial, even if it happened to be the last match.
                    return select(query, matches, false, inspected, "result_limit", cause);
                }
            }
            if (query.hierarchy() != Hierarchy.SUPERCLASSES) break;
        }
        return select(query, matches, complete, inspected, reason, cause);
    }
    private Member[] members(Class<?> owner, QuerySpec query) {
        boolean publicLookup = query.hierarchy() == Hierarchy.JAVA_PUBLIC;
        switch (query.kind()) {
            case METHOD: return publicLookup ? owner.getMethods() : owner.getDeclaredMethods();
            case FIELD: return publicLookup ? owner.getFields() : owner.getDeclaredFields();
            case CONSTRUCTOR: return owner.getDeclaredConstructors();
            default: throw new AssertionError(query.kind());
        }
    }
    private boolean cheapMatches(Member member, QuerySpec query) {
        String name = member instanceof Constructor<?> ? "<init>" : member.getName();
        if (query.name() != null && !query.name().equals(name)) return false;
        if (query.staticOnly() != null && query.staticOnly() != Modifier.isStatic(member.getModifiers())) return false;
        if (query.parameterCount() >= 0) {
            int count = member instanceof Method ? ((Method) member).getParameterCount() : member instanceof Constructor<?> ? ((Constructor<?>) member).getParameterCount() : 0;
            if (query.parameterCount() != count) return false;
        }
        if (query.synthetic() != null && query.synthetic() != member.isSynthetic()) return false;
        return query.bridge() == null || query.bridge() == (member instanceof Method && ((Method) member).isBridge());
    }
    private boolean matches(MethodSchema schema, QuerySpec query, Class<?>[] parameters, Class<?>[] arguments) {
        if (parameters != null && !schema.parametersEqual(parameters)) return false;
        if (arguments != null && !schema.argumentsMatch(arguments, query.compatibility())) return false;
        return query.returnClass() == null || query.returnClass() == schema.type();
    }
    private Outcome provider(QuerySpec query, Class<?> owner, ClassLoader loader, ProviderRegistry registry, String fingerprint) {
        if (registry == null || fingerprint == null) return failure(ResolutionStatus.UNAVAILABLE, query, "provider_or_code_identity_missing", null);
        CandidateProvider provider = registry.provider(query.providerId());
        if (provider == null) return failure(ResolutionStatus.UNAVAILABLE, query, "provider_not_installed", null);
        CandidateBatch batch;
        try { batch = Objects.requireNonNull(provider.discover(new CandidateRequest(query, fingerprint)), "provider batch"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return failure(ResolutionStatus.CANCELLED, query, "provider_interrupted", e); }
        catch (java.util.concurrent.CancellationException e) { return failure(ResolutionStatus.CANCELLED, query, "provider_cancelled", e); }
        catch (Exception e) { return failure(ResolutionStatus.PARTIAL, query, "provider_failed", e); }
        if (!fingerprint.equals(batch.codeFingerprint())) return failure(ResolutionStatus.STALE, query, "provider_code_identity_mismatch", null);
        List<Entry> matches = new ArrayList<>(); Set<Member> seen = new HashSet<>();
        int inspected = 0; boolean complete = batch.complete(); String reason = complete ? "provider_claim_runtime_verified" : "provider_partial"; Throwable cause = null;
        Class<?>[] parameters = query.parameterClasses(), arguments = query.argumentClasses();
        for (MemberDescriptor descriptor : batch.candidates()) {
            if (Thread.currentThread().isInterrupted()) return outcome(ResolutionStatus.CANCELLED, query, matches, false, inspected, "interrupted", null);
            if (inspected >= query.candidateBudget()) { complete = false; reason = "candidate_budget"; break; }
            inspected++; candidateChecks.incrementAndGet();
            Outcome verified = resolve(QuerySpec.exact(descriptor), loader, null, null);
            if (verified.status != ResolutionStatus.RESOLVED) { complete = false; reason = "provider_candidate_unavailable"; continue; }
            Entry entry = verified.entries.get(0);
            if (!inDomain(owner, entry.member.getDeclaringClass(), query) || (query.hierarchy() == Hierarchy.JAVA_PUBLIC && !Modifier.isPublic(entry.member.getModifiers()))) { complete = false; reason = "provider_candidate_outside_domain"; continue; }
            if (descriptor.kind() != query.kind() || !seen.add(entry.member)) continue;
            if (query.exactDescriptor() != null && !query.exactDescriptor().equals(descriptor)) continue;
            if (!cheapMatches(entry.member, query)) continue;
            MethodSchema schema = schema(entry.member);
            if (matches(schema, query, parameters, arguments)) matches.add(entry);
            if (query.selection() == Selection.UNIQUE && matches.size() >= 2) break;
            if (matches.size() >= query.resultLimit()) { complete = false; reason = "result_limit"; break; }
        }
        if (complete && !(query.selection() == Selection.UNIQUE && matches.size() > 1)) {
            Set<Member> provided = new HashSet<>();
            for (Entry match : matches) provided.add(match.member);
            complete = ProviderCoverageVerifier.verifies(query, owner, provided);
            reason = complete ? "provider_claim_independently_verified_java_query" : "provider_coverage_not_proven";
        }
        Outcome result = select(query, matches, complete, inspected, reason, cause);
        return new Outcome(result.status, result.entries, new Coverage("provider:" + provider.descriptor().id() + ":" + batch.universe(), result.coverage.complete(), inspected, reason), reason, result.cause);
    }
    private boolean inDomain(Class<?> requested, Class<?> declaring, QuerySpec query) {
        if (query.hierarchy() == Hierarchy.JAVA_PUBLIC) return declaring.isAssignableFrom(requested);
        int depth = 0;
        for (Class<?> current = requested; current != null && depth++ < query.depthLimit(); current = current.getSuperclass()) {
            if (current == declaring) return true;
            if (query.hierarchy() != Hierarchy.SUPERCLASSES) break;
        }
        return false;
    }
    private Outcome select(QuerySpec query, List<Entry> matches, boolean complete, int inspected, String reason, Throwable cause) {
        ResolutionStatus status;
        if (query.selection() == Selection.UNIQUE && matches.size() > 1) status = ResolutionStatus.AMBIGUOUS;
        else if (query.selection() == Selection.FIRST && !matches.isEmpty() && reason.equals("first_in_reflection_order")) status = ResolutionStatus.RESOLVED;
        else if (!complete) status = ResolutionStatus.PARTIAL;
        else if (matches.isEmpty()) status = ResolutionStatus.NOT_FOUND_IN_VISIBLE_UNIVERSE;
        else status = query.selection() == Selection.ALL ? ResolutionStatus.ALL_MATCHES : ResolutionStatus.RESOLVED;
        if (status == ResolutionStatus.RESOLVED && query.selection() == Selection.FIRST && matches.size() > 1) matches = Collections.singletonList(matches.get(0));
        return outcome(status, query, matches, complete, inspected, reason, cause);
    }
    private static Outcome failure(ResolutionStatus status, QuerySpec query, String reason, Throwable cause) { return outcome(status, query, Collections.emptyList(), false, 0, reason, cause); }
    private static Outcome outcome(ResolutionStatus status, QuerySpec query, List<Entry> matches, boolean complete, int inspected, String reason, Throwable cause) {
        return new Outcome(status, matches, new Coverage("java-visible:" + query.owner() + ":" + query.hierarchy(), complete, inspected, reason), reason, cause == null ? null : cause.getClass().getName());
    }
    public static final class Entry {
        public final Member member;
        public final MemberDescriptor descriptor;
        public Entry(Member member, MemberDescriptor descriptor) { this.member = member; this.descriptor = descriptor; }
    }
    public static final class Outcome {
        public final ResolutionStatus status;
        public final List<Entry> entries;
        public final Coverage coverage;
        public final String reason, cause;
        public Outcome(ResolutionStatus status, List<Entry> entries, Coverage coverage, String reason, String cause) {
            this.status = status; this.entries = Collections.unmodifiableList(new ArrayList<>(entries)); this.coverage = coverage; this.reason = reason; this.cause = cause;
        }
        static Outcome single(Member member, MemberDescriptor descriptor, QuerySpec query, int inspected) {
            return outcome(ResolutionStatus.RESOLVED, query, Collections.singletonList(new Entry(member, descriptor)), true, inspected, "exact_runtime_verified", null);
        }
    }
}
