package io.github.jichuo1.meguviz.ref.scope;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import io.github.jichuo1.meguviz.ref.api.evidence.*;
import io.github.jichuo1.meguviz.ref.api.query.*;
import io.github.jichuo1.meguviz.ref.api.result.*;
import io.github.jichuo1.meguviz.ref.runtime.invoke.*;
import io.github.jichuo1.meguviz.ref.runtime.resolve.*;
import io.github.jichuo1.meguviz.ref.spi.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Synchronous code lifetime. close rejects new work without waiting for in-flight target methods. */
public final class CodeScope implements AutoCloseable {
    public enum State { OPEN, RETIRING, CLOSED }
    private final Object lock = new Object();
    private final AccessPolicy accessPolicy;
    private final int capacity;
    private ClassLoader loader;
    private State state = State.OPEN;
    private String identity = UUID.randomUUID().toString();
    private long codeEpoch, nextSlot = 1, cacheHits;
    private int active;
    private boolean resolved;
    private final Map<QuerySpec, ResolutionResult> cache = new LinkedHashMap<>(16, .75f, true);
    private final Map<Long, Slot> slots = new HashMap<>();
    private final Map<Member, Long> memberSlots = new HashMap<>();
    private ReflectionResolver resolver;
    private QueryCoordinator<QuerySpec, ResolutionResult> coordinator;
    private ProviderRegistry providers;
    private String codeFingerprint;
    private volatile String closeFailureType;
    private CodeScope(ClassLoader loader, AccessPolicy accessPolicy, int capacity) {
        if (capacity < 1 || capacity > 1000000) throw new IllegalArgumentException("capacity");
        this.loader = loader; this.accessPolicy = Objects.requireNonNull(accessPolicy); this.capacity = capacity;
        resolver = new ReflectionResolver(capacity); coordinator = new QueryCoordinator<>(Math.min(capacity, 256));
    }
    public static CodeScope open(ClassLoader loader) { return new CodeScope(loader, AccessPolicy.PRESERVE_JAVA_ACCESS, 4096); }
    public static CodeScope open(ClassLoader loader, AccessPolicy accessPolicy, int capacity) { return new CodeScope(loader, accessPolicy, capacity); }
    public AccessPolicy accessPolicy() { return accessPolicy; }
    public boolean usesLoader(ClassLoader candidate) { synchronized (lock) { return state == State.OPEN && loader == candidate; } }
    public State state() { synchronized (lock) { return state; } }
    public long codeEpoch() { synchronized (lock) { return codeEpoch; } }
    public String identity() { synchronized (lock) { return identity; } }
    /** Provider teardown failures remain observable without replacing an in-flight target's exception. */
    public String closeFailureType() { return closeFailureType; }
    /** Registry ownership transfers once, before resolution; create a new scope to install a new snapshot. */
    public void installProviders(ProviderRegistry registry, CodeIdentity codeIdentity) {
        Objects.requireNonNull(registry); Objects.requireNonNull(codeIdentity);
        if (codeIdentity.strength() != CodeIdentity.Strength.STRONG) throw new IllegalArgumentException("provider requires strong code identity");
        synchronized (lock) {
            if (state != State.OPEN || resolved || providers != null) throw new IllegalStateException("provider snapshot already frozen");
            registry.claim(); providers = registry; codeFingerprint = codeIdentity.fingerprint();
        }
    }
    public ResolutionResult resolve(QuerySpec query) {
        Objects.requireNonNull(query);
        Context context;
        synchronized (lock) {
            if (state != State.OPEN) return failure(query, ResolutionStatus.STALE, "scope_closed");
            if (Thread.currentThread().isInterrupted()) return failure(query, ResolutionStatus.CANCELLED, "interrupted");
            resolved = true;
            ResolutionResult cached = cache.get(query);
            if (cached != null) { cacheHits++; return cached; }
            active++;
            context = new Context(loader, identity, codeEpoch, resolver, coordinator, providers, codeFingerprint);
        }
        try {
            return context.coordinator.execute(query, () -> resolveUncached(query, context), 2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return failure(query, ResolutionStatus.CANCELLED, "wait_interrupted");
        } catch (TimeoutException e) { return failure(query, ResolutionStatus.PARTIAL, "single_flight_timeout"); }
        catch (CancellationException e) { return failure(query, ResolutionStatus.STALE, "scope_invalidated"); }
        catch (RejectedExecutionException e) { return failure(query, ResolutionStatus.PARTIAL, "single_flight_capacity"); }
        catch (IllegalStateException e) {
            if ("recursive or cyclic query".equals(e.getMessage())) return failure(query, ResolutionStatus.PARTIAL, "recursive_or_cyclic_query");
            throw e;
        } finally { release(); }
    }
    private ResolutionResult resolveUncached(QuerySpec query, Context context) {
        synchronized (lock) {
            if (!current(context)) return failure(query, ResolutionStatus.STALE, "scope_invalidated");
            ResolutionResult cached = cache.get(query);
            if (cached != null) { cacheHits++; return cached; }
        }
        ReflectionResolver.Outcome outcome = context.resolver.resolve(query, context.loader, context.providers, context.codeFingerprint);
        synchronized (lock) {
            if (!current(context)) return failure(query, ResolutionStatus.STALE, "scope_invalidated");
            Set<Member> missing = new HashSet<>();
            for (ReflectionResolver.Entry entry : outcome.entries) if (!memberSlots.containsKey(entry.member)) missing.add(entry.member);
            if ((long) slots.size() + missing.size() > capacity) return failure(query, ResolutionStatus.PARTIAL, "binding_capacity");
            List<ResolvedMember> members = new ArrayList<>();
            List<MemberDescriptor> descriptors = new ArrayList<>();
            for (ReflectionResolver.Entry entry : outcome.entries) {
                Long slot = memberSlots.get(entry.member);
                if (slot == null) { slot = nextSlot++; memberSlots.put(entry.member, slot); slots.put(slot, new Slot(entry.member, entry.descriptor)); }
                members.add(new ResolvedMember(identity, codeEpoch, slot, entry.descriptor)); descriptors.add(entry.descriptor);
            }
            ResolutionReceipt receipt = new ResolutionReceipt(query.summary(), query.selection(), outcome.status, outcome.coverage, descriptors, outcome.reason, outcome.cause);
            ResolutionResult result = new ResolutionResult(outcome.status, members, receipt);
            if (outcome.coverage.complete() && (outcome.status == ResolutionStatus.RESOLVED || outcome.status == ResolutionStatus.ALL_MATCHES)) {
                cache.put(query, result);
                if (cache.size() > capacity) cache.remove(cache.keySet().iterator().next());
            }
            return result;
        }
    }
    private boolean current(Context context) { return state == State.OPEN && identity.equals(context.identity) && codeEpoch == context.epoch; }
    private static ResolutionResult failure(QuerySpec query, ResolutionStatus status, String reason) {
        Coverage coverage = new Coverage("java-visible:" + query.owner() + ":" + query.hierarchy(), false, 0, reason);
        return new ResolutionResult(status, Collections.emptyList(), new ResolutionReceipt(query.summary(), query.selection(), status, coverage, Collections.emptyList(), reason, null));
    }
    public MethodPlan bindMethod(ResolvedMember member) throws IllegalAccessException { prepare(member, MemberKind.METHOD); return new MethodPlan(this, member); }
    public FieldPlan bindField(ResolvedMember member) throws IllegalAccessException { prepare(member, MemberKind.FIELD); return new FieldPlan(this, member); }
    public ConstructorPlan bindConstructor(ResolvedMember member) throws IllegalAccessException { prepare(member, MemberKind.CONSTRUCTOR); return new ConstructorPlan(this, member); }
    private void prepare(ResolvedMember token, MemberKind kind) throws IllegalAccessException {
        if (token.descriptor().kind() != kind) throw new IllegalArgumentException("wrong member kind");
        Member member = acquire(token);
        try { prepareAccess(member); } finally { release(); }
    }
    private void prepareAccess(Member member) throws IllegalAccessException {
        if (accessPolicy != AccessPolicy.TRY_SUPPRESS_JAVA_CHECKS) return;
        try { ((AccessibleObject) member).setAccessible(true); }
        catch (RuntimeException e) {
            IllegalAccessException denied = new IllegalAccessException("public access suppression rejected: " + member.getName()); denied.initCause(e); throw denied;
        }
    }
    /** Deliberate ownership escape: this raw reference can outlive close and retain its defining loader. */
    public Member exportRaw(ResolvedMember token) throws IllegalAccessException {
        Member member = acquire(token);
        try { prepareAccess(member); return member; } finally { release(); }
    }
    private Member acquire(ResolvedMember token) {
        synchronized (lock) {
            if (state != State.OPEN || !identity.equals(token.scopeId()) || codeEpoch != token.codeEpoch()) throw new StalePlanException("scope identity or epoch is stale");
            Slot slot = slots.get(token.slot());
            if (slot == null) throw new StalePlanException("binding no longer exists");
            // A public token is not a security capability, but malformed tokens must not select another slot.
            if (!token.descriptor().equals(slot.descriptor)) throw new StalePlanException("binding descriptor mismatch");
            active++; return slot.member;
        }
    }
    public Object invokeMethod(ResolvedMember token, Object receiver, Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Member member = acquire(token);
        try { return ((Method) member).invoke(receiver, arguments); } finally { release(); }
    }
    public Object getField(ResolvedMember token, Object receiver) throws IllegalAccessException {
        Member member = acquire(token);
        try { return ((Field) member).get(receiver); } finally { release(); }
    }
    public void setField(ResolvedMember token, Object receiver, Object value) throws IllegalAccessException {
        Member member = acquire(token);
        try { ((Field) member).set(receiver, value); } finally { release(); }
    }
    public Object construct(ResolvedMember token, Object[] arguments) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Member member = acquire(token);
        try { return ((Constructor<?>) member).newInstance(arguments); } finally { release(); }
    }
    public void advanceCodeEpoch(String reason) {
        if (reason == null || reason.isEmpty()) throw new IllegalArgumentException("invalidation reason");
        QueryCoordinator<QuerySpec, ResolutionResult> oldCoordinator;
        synchronized (lock) {
            if (state != State.OPEN) throw new StalePlanException("scope closed");
            if (codeEpoch == Long.MAX_VALUE) { identity = UUID.randomUUID().toString(); codeEpoch = 0; } else codeEpoch++;
            cache.clear(); slots.clear(); memberSlots.clear(); resolver.clear();
            resolver = new ReflectionResolver(capacity); codeFingerprint = null;
            oldCoordinator = coordinator; coordinator = new QueryCoordinator<>(Math.min(capacity, 256));
        }
        oldCoordinator.close();
    }
    public Map<String, Long> diagnostics() {
        synchronized (lock) {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("cacheHits", cacheHits); values.put("cachedQueries", (long) cache.size()); values.put("bindings", (long) slots.size());
            values.put("schemas", (long) resolver.schemaSize()); values.put("parameterReads", resolver.parameterReads());
            values.put("candidateChecks", resolver.candidateChecks()); values.put("lookups", resolver.lookups()); values.put("inFlight", (long) active);
            return Collections.unmodifiableMap(values);
        }
    }
    @Override public void close() {
        ProviderRegistry retired = null;
        QueryCoordinator<QuerySpec, ResolutionResult> previous;
        synchronized (lock) {
            if (state != State.OPEN) return;
            state = State.RETIRING; loader = null; cache.clear(); slots.clear(); memberSlots.clear(); resolver.clear(); codeFingerprint = null;
            previous = coordinator;
            if (active == 0) { state = State.CLOSED; retired = providers; providers = null; }
        }
        previous.close();
        if (retired != null) {
            try { retired.close(); }
            catch (RuntimeException failure) { closeFailureType = failure.getClass().getName(); throw failure; }
        }
    }
    private void release() {
        ProviderRegistry retired = null;
        synchronized (lock) {
            active--;
            if (active == 0 && state == State.RETIRING) { state = State.CLOSED; retired = providers; providers = null; resolver.clear(); }
        }
        if (retired != null) {
            try { retired.close(); }
            catch (RuntimeException failure) { closeFailureType = failure.getClass().getName(); }
        }
    }
    private static final class Slot {
        final Member member;
        final MemberDescriptor descriptor;
        Slot(Member member, MemberDescriptor descriptor) { this.member = member; this.descriptor = descriptor; }
    }
    private static final class Context {
        final ClassLoader loader;
        final String identity, codeFingerprint;
        final long epoch;
        final ReflectionResolver resolver;
        final QueryCoordinator<QuerySpec, ResolutionResult> coordinator;
        final ProviderRegistry providers;
        Context(ClassLoader loader, String identity, long epoch, ReflectionResolver resolver, QueryCoordinator<QuerySpec, ResolutionResult> coordinator, ProviderRegistry providers, String codeFingerprint) {
            this.loader = loader; this.identity = identity; this.epoch = epoch; this.resolver = resolver; this.coordinator = coordinator; this.providers = providers; this.codeFingerprint = codeFingerprint;
        }
    }
}
