package io.github.jichuo1.meguviz.ref.api.evidence;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.query.Selection;
import io.github.jichuo1.meguviz.ref.api.result.*;
import java.util.*;

/** Diagnostic values only. Runtime identities and Throwable objects are deliberately excluded. */
public final class ResolutionReceipt {
    public static final int SCHEMA_MAJOR = 1;
    private final String querySummary, reason, causeType;
    private final Selection selection;
    private final ResolutionStatus status;
    private final Coverage coverage;
    private final List<MemberDescriptor> descriptors;
    public ResolutionReceipt(String querySummary, Selection selection, ResolutionStatus status, Coverage coverage, List<MemberDescriptor> descriptors, String reason, String causeType) {
        this.querySummary = Objects.requireNonNull(querySummary); this.selection = Objects.requireNonNull(selection);
        this.status = Objects.requireNonNull(status); this.coverage = Objects.requireNonNull(coverage);
        this.descriptors = Collections.unmodifiableList(new ArrayList<>(descriptors)); this.reason = Objects.requireNonNull(reason); this.causeType = causeType;
    }
    public String querySummary() { return querySummary; }
    public Selection selection() { return selection; }
    public ResolutionStatus status() { return status; }
    public Coverage coverage() { return coverage; }
    public List<MemberDescriptor> descriptors() { return descriptors; }
    public String reason() { return reason; }
    public String causeType() { return causeType; }
}
