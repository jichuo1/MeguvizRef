package io.github.jichuo1.meguviz.ref.api.result;
public final class ResolutionException extends IllegalStateException {
    private final ResolutionStatus status;
    public ResolutionException(ResolutionStatus status, String reason) { super(status + ": " + reason); this.status = status; }
    public ResolutionStatus status() { return status; }
}
