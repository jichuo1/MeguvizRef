package io.github.jichuo1.meguviz.ref.api.result;

import io.github.jichuo1.meguviz.ref.api.evidence.ResolutionReceipt;
import java.util.*;

public final class ResolutionResult {
    private final ResolutionStatus status;
    private final List<ResolvedMember> members;
    private final ResolutionReceipt receipt;
    public ResolutionResult(ResolutionStatus status, List<ResolvedMember> members, ResolutionReceipt receipt) {
        this.status = Objects.requireNonNull(status); this.members = Collections.unmodifiableList(new ArrayList<>(members)); this.receipt = Objects.requireNonNull(receipt);
    }
    public ResolutionStatus status() { return status; }
    public List<ResolvedMember> members() { return members; }
    public ResolutionReceipt receipt() { return receipt; }
    public ResolvedMember requireSingle() {
        if (status != ResolutionStatus.RESOLVED || members.size() != 1) throw new ResolutionException(status, receipt.reason());
        return members.get(0);
    }
}
