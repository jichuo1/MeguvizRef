package io.github.jichuo1.meguviz.ref.spi;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import java.util.*;

/** complete is a provider claim about its universe; runtime verification never upgrades it to global coverage. */
public final class CandidateBatch {
    private final List<MemberDescriptor> candidates;
    private final String codeFingerprint, universe;
    private final boolean complete;
    public CandidateBatch(List<MemberDescriptor> candidates, String codeFingerprint, String universe, boolean complete) {
        if (candidates.size() > 1000000) throw new IllegalArgumentException("candidate limit");
        for (MemberDescriptor candidate : candidates) Objects.requireNonNull(candidate, "candidate descriptor");
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.codeFingerprint = Objects.requireNonNull(codeFingerprint); this.universe = Objects.requireNonNull(universe); this.complete = complete;
    }
    public List<MemberDescriptor> candidates() { return candidates; }
    public String codeFingerprint() { return codeFingerprint; }
    public String universe() { return universe; }
    public boolean complete() { return complete; }
}
