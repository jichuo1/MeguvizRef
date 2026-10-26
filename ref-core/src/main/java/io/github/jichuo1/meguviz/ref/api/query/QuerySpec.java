package io.github.jichuo1.meguviz.ref.api.query;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import java.util.*;

/** Immutable query. Class-valued builders are explicitly runtime-only. No receiver or argument values are retained. */
public final class QuerySpec {
    public static final int SCHEMA_MAJOR = 1;
    public static final String RULE_VERSION = "mvx-ref-1";
    private final String owner, name;
    private final Class<?> runtimeOwner, returnClass;
    private final Class<?>[] parameterClasses, argumentClasses;
    private final MemberKind kind;
    private final MemberDescriptor exact;
    private final Hierarchy hierarchy;
    private final Selection selection;
    private final CompatibilityPolicy compatibility;
    private final Boolean staticOnly, bridge, synthetic;
    private final int parameterCount, candidateBudget, resultLimit, depthLimit;
    private final String providerId;
    private QuerySpec(Builder b) {
        owner = b.owner; runtimeOwner = b.runtimeOwner; kind = b.kind; name = b.name;
        parameterClasses = b.parameterClasses == null ? null : b.parameterClasses.clone();
        argumentClasses = b.argumentClasses == null ? null : b.argumentClasses.clone();
        returnClass = b.returnClass; exact = b.exact; hierarchy = b.hierarchy; selection = b.selection;
        compatibility = b.compatibility; staticOnly = b.staticOnly; bridge = b.bridge; synthetic = b.synthetic;
        parameterCount = b.parameterCount; candidateBudget = b.candidateBudget; resultLimit = b.resultLimit; depthLimit = b.depthLimit; providerId = b.providerId;
        if (selection == Selection.EXACT && exact == null) throw new IllegalArgumentException("EXACT requires a complete portable descriptor");
        if (kind == MemberKind.CONSTRUCTOR && hierarchy != Hierarchy.DECLARED) throw new IllegalArgumentException("constructors are not inherited");
    }
    public static QuerySpec exact(MemberDescriptor descriptor) {
        return new Builder(descriptor.owner(), null, descriptor.kind()).exact(descriptor).build();
    }
    public static Builder builder(Class<?> owner, MemberKind kind) { return new Builder(owner.getName(), owner, kind); }
    public static Builder builder(String owner, MemberKind kind) { return new Builder(owner, null, kind); }
    public String owner() { return owner; }
    public String name() { return name; }
    public Class<?> runtimeOwner() { return runtimeOwner; }
    public MemberKind kind() { return kind; }
    public MemberDescriptor exactDescriptor() { return exact; }
    public Hierarchy hierarchy() { return hierarchy; }
    public Selection selection() { return selection; }
    public CompatibilityPolicy compatibility() { return compatibility; }
    public Boolean staticOnly() { return staticOnly; }
    public Boolean bridge() { return bridge; }
    public Boolean synthetic() { return synthetic; }
    public int parameterCount() { return parameterCount; }
    public int candidateBudget() { return candidateBudget; }
    public int resultLimit() { return resultLimit; }
    public int depthLimit() { return depthLimit; }
    public String providerId() { return providerId; }
    public Class<?>[] parameterClasses() { return parameterClasses == null ? null : parameterClasses.clone(); }
    public Class<?>[] argumentClasses() { return argumentClasses == null ? null : argumentClasses.clone(); }
    public Class<?> returnClass() { return returnClass; }
    public boolean portable() { return runtimeOwner == null && parameterClasses == null && argumentClasses == null && returnClass == null; }
    /** Descriptive value only, never a runtime cache key (Class identity is significant). */
    public String summary() { return RULE_VERSION + ":" + kind + ":" + owner + ":" + name + ":" + selection + ":" + hierarchy + ":" + exact + ":" + staticOnly + ":" + bridge + ":" + synthetic + ":" + parameterCount + ":" + candidateBudget + ":" + resultLimit + ":" + depthLimit + ":" + compatibility + ":" + Arrays.toString(parameterClasses) + ":" + Arrays.toString(argumentClasses) + ":" + returnClass + ":" + providerId; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof QuerySpec)) return false;
        QuerySpec q = (QuerySpec) other;
        return runtimeOwner == q.runtimeOwner && returnClass == q.returnClass && Arrays.equals(parameterClasses, q.parameterClasses) && Arrays.equals(argumentClasses, q.argumentClasses)
                && owner.equals(q.owner) && Objects.equals(name, q.name) && kind == q.kind && Objects.equals(exact, q.exact)
                && hierarchy == q.hierarchy && selection == q.selection && compatibility == q.compatibility
                && Objects.equals(staticOnly, q.staticOnly) && Objects.equals(bridge, q.bridge) && Objects.equals(synthetic, q.synthetic)
                && parameterCount == q.parameterCount && candidateBudget == q.candidateBudget && resultLimit == q.resultLimit
                && depthLimit == q.depthLimit && Objects.equals(providerId, q.providerId);
    }
    @Override public int hashCode() { return Objects.hash(owner, name, kind, exact, hierarchy, selection, compatibility, staticOnly, bridge, synthetic, parameterCount, candidateBudget, resultLimit, depthLimit, providerId, runtimeOwner, returnClass, Arrays.hashCode(parameterClasses), Arrays.hashCode(argumentClasses)); }
    public static final class Builder {
        private final String owner;
        private final Class<?> runtimeOwner;
        private final MemberKind kind;
        private String name, providerId;
        private Class<?> returnClass;
        private Class<?>[] parameterClasses, argumentClasses;
        private MemberDescriptor exact;
        private Hierarchy hierarchy = Hierarchy.DECLARED;
        private Selection selection = Selection.UNIQUE;
        private CompatibilityPolicy compatibility;
        private Boolean staticOnly, bridge, synthetic;
        private int parameterCount = -1, candidateBudget = 16384, resultLimit = 4096, depthLimit = 256;
        private Builder(String owner, Class<?> runtimeOwner, MemberKind kind) {
            this.owner = MemberDescriptor.field(owner, "_", "I").owner(); this.runtimeOwner = runtimeOwner; this.kind = Objects.requireNonNull(kind);
        }
        private Builder exact(MemberDescriptor descriptor) { exact = descriptor; name = descriptor.name(); selection = Selection.EXACT; return this; }
        public Builder name(String value) { if (value == null || value.length() > 1024 || value.isEmpty()) throw new IllegalArgumentException("name"); name = value; return this; }
        public Builder parameters(Class<?>... values) {
            if (values.length > 255) throw new IllegalArgumentException("parameters");
            for (Class<?> value : values) if (value == null || value == void.class) throw new IllegalArgumentException("parameter type");
            parameterClasses = values.clone(); parameterCount = values.length; return this;
        }
        public Builder arguments(CompatibilityPolicy policy, Object... arguments) {
            if (arguments.length > 255) throw new IllegalArgumentException("arguments");
            compatibility = Objects.requireNonNull(policy); argumentClasses = new Class<?>[arguments.length];
            for (int i = 0; i < arguments.length; i++) argumentClasses[i] = arguments[i] == null ? null : arguments[i].getClass();
            parameterCount = arguments.length; return this;
        }
        public Builder returns(Class<?> type) { returnClass = Objects.requireNonNull(type); return this; }
        public Builder hierarchy(Hierarchy value) { hierarchy = Objects.requireNonNull(value); return this; }
        public Builder selection(Selection value) { selection = Objects.requireNonNull(value); return this; }
        public Builder staticOnly(boolean value) { staticOnly = value; return this; }
        public Builder bridge(boolean value) { bridge = value; return this; }
        public Builder synthetic(boolean value) { synthetic = value; return this; }
        public Builder parameterCount(int count) { if (count < 0 || count > 255) throw new IllegalArgumentException("arity"); parameterCount = count; return this; }
        public Builder candidateBudget(int count) { if (count < 1 || count > 1000000) throw new IllegalArgumentException("budget"); candidateBudget = count; return this; }
        public Builder resultLimit(int count) { if (count < 1 || count > 1000000) throw new IllegalArgumentException("limit"); resultLimit = count; return this; }
        public Builder depthLimit(int count) { if (count < 1 || count > 1024) throw new IllegalArgumentException("depth"); depthLimit = count; return this; }
        public Builder provider(String id) { if (id == null || id.isEmpty() || id.length() > 256) throw new IllegalArgumentException("provider ID"); providerId = id; return this; }
        public QuerySpec build() { return new QuerySpec(this); }
    }
}
