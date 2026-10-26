package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import io.github.jichuo1.meguviz.ref.api.query.AccessPolicy;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import java.util.Objects;

/** An exact-descriptor hint, never a persisted proof of structural uniqueness. */
public final class StoredResolution {
    public static final int SCHEMA_MAJOR = 1;
    public static final int SCHEMA_MINOR = 0;
    private final CodeIdentity identity;
    private final MemberDescriptor descriptor;
    private final AccessPolicy accessPolicy;
    private final int requiredModifiers, forbiddenModifiers;
    public StoredResolution(CodeIdentity identity, MemberDescriptor descriptor, AccessPolicy accessPolicy,
                            int requiredModifiers, int forbiddenModifiers) {
        this.identity = Objects.requireNonNull(identity);
        this.descriptor = Objects.requireNonNull(descriptor);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
        if (identity.strength() != CodeIdentity.Strength.STRONG) throw new IllegalArgumentException("Strong content identity required");
        if (!identity.ruleVersion().equals(QuerySpec.RULE_VERSION)) throw new IllegalArgumentException("Unknown rule version");
        if ((requiredModifiers & forbiddenModifiers) != 0) throw new IllegalArgumentException("Conflicting flags");
        this.requiredModifiers = requiredModifiers;
        this.forbiddenModifiers = forbiddenModifiers;
    }
    public CodeIdentity identity() { return identity; }
    public MemberDescriptor descriptor() { return descriptor; }
    public AccessPolicy accessPolicy() { return accessPolicy; }
    public int requiredModifiers() { return requiredModifiers; }
    public int forbiddenModifiers() { return forbiddenModifiers; }
    public String queryFingerprint() {
        java.util.List<String> parameters = new java.util.ArrayList<>();
        for (io.github.jichuo1.meguviz.ref.api.descriptor.TypeDescriptor type : descriptor.parameters())
            parameters.add(type.value());
        return CodeIdentities.sha256(BoundedJson.encode(java.util.Arrays.asList(
                QuerySpec.RULE_VERSION, descriptor.kind().name(), descriptor.owner(), descriptor.name(),
                parameters, descriptor.type().value(), accessPolicy.name(), requiredModifiers, forbiddenModifiers)));
    }
}
