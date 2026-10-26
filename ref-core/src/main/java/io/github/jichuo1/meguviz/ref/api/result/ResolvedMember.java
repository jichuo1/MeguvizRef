package io.github.jichuo1.meguviz.ref.api.result;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import java.util.Objects;

/** Scope token, not a raw Member. An escaped token does not retain a loader or receiver. */
public final class ResolvedMember {
    private final String scopeId;
    private final long codeEpoch, slot;
    private final MemberDescriptor descriptor;
    public ResolvedMember(String scopeId, long codeEpoch, long slot, MemberDescriptor descriptor) {
        this.scopeId = Objects.requireNonNull(scopeId); this.codeEpoch = codeEpoch; this.slot = slot; this.descriptor = Objects.requireNonNull(descriptor);
    }
    public String scopeId() { return scopeId; }
    public long codeEpoch() { return codeEpoch; }
    public long slot() { return slot; }
    public MemberDescriptor descriptor() { return descriptor; }
}
