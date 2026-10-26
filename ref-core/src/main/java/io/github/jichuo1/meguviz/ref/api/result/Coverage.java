package io.github.jichuo1.meguviz.ref.api.result;

import java.util.Objects;

/** Completeness is limited to the named visible universe, never a physical-existence proof. */
public final class Coverage {
    private final String universe;
    private final boolean complete;
    private final int inspected;
    private final String reason;
    public Coverage(String universe, boolean complete, int inspected, String reason) {
        this.universe = Objects.requireNonNull(universe); this.complete = complete;
        if (inspected < 0) throw new IllegalArgumentException("inspected");
        this.inspected = inspected; this.reason = Objects.requireNonNull(reason);
    }
    public String universe() { return universe; }
    public boolean complete() { return complete; }
    public int inspected() { return inspected; }
    public String reason() { return reason; }
}
