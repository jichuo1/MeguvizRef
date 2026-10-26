package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import io.github.jichuo1.meguviz.ref.api.query.AccessPolicy;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.api.result.ResolutionResult;
import io.github.jichuo1.meguviz.ref.api.result.ResolutionStatus;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import io.github.jichuo1.meguviz.ref.spi.ReceiptStore;
import java.io.IOException;
import java.util.Optional;

public final class PersistentResolutions {
    public enum Status { REBOUND, MISS, INVALID_RECORD, STALE_IDENTITY, POLICY_MISMATCH, IO_FAILURE, UNAVAILABLE, CANCELLED }
    public static final class LoadResult {
        private final Status status;
        private final String reason;
        private final ResolutionResult resolution;
        private LoadResult(Status status, String reason, ResolutionResult resolution) {
            this.status = status; this.reason = reason; this.resolution = resolution;
        }
        public Status status() { return status; }
        public String reason() { return reason; }
        public Optional<ResolutionResult> resolution() { return Optional.ofNullable(resolution); }
    }
    private PersistentResolutions() { }
    /** Current identity and loader must describe the same, owner-synchronized code domain as scope. */
    public static LoadResult restore(ReceiptStore store, String key, CodeIdentity current, AccessPolicy policy,
                                     ClassLoader loader, CodeScope scope) {
        if (Thread.currentThread().isInterrupted()) return result(Status.CANCELLED, "interrupted");
        final StoredResolution record;
        try {
            Optional<byte[]> bytes = store.read(key);
            if (!bytes.isPresent()) return result(Status.MISS, "no_record");
            record = ReceiptJson.decode(bytes.get());
        } catch (java.io.InterruptedIOException interrupted) { return result(Status.CANCELLED, "interrupted"); }
        catch (IOException failure) { return result(Status.IO_FAILURE, failure.getClass().getName()); }
        catch (IllegalArgumentException invalid) { return result(Status.INVALID_RECORD, invalid.getMessage()); }
        if (!record.identity().matchesStrong(current)) return result(Status.STALE_IDENTITY, "content_or_loading_configuration_changed");
        if (record.accessPolicy() != policy || scope.accessPolicy() != policy)
            return result(Status.POLICY_MISMATCH, "access_policy_changed");
        if (!scope.usesLoader(loader)) return result(Status.STALE_IDENTITY, "scope_loader_mismatch_or_closed");
        IndependentVerifier.Status verified = IndependentVerifier.verify(loader, record.descriptor(), record.requiredModifiers(), record.forbiddenModifiers());
        if (verified != IndependentVerifier.Status.VERIFIED) return result(Status.UNAVAILABLE, "independent_validation_" + verified.name());
        ResolutionResult resolution = scope.resolve(QuerySpec.exact(record.descriptor()));
        return new LoadResult(resolution.status() == ResolutionStatus.RESOLVED ? Status.REBOUND : Status.UNAVAILABLE,
                resolution.receipt().reason(), resolution);
    }
    private static LoadResult result(Status status, String reason) { return new LoadResult(status, reason, null); }
}
