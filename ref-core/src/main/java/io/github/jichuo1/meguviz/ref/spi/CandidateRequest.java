package io.github.jichuo1.meguviz.ref.spi;

import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import java.util.Objects;

public final class CandidateRequest {
    private final QuerySpec query;
    private final String codeFingerprint;
    public CandidateRequest(QuerySpec query, String codeFingerprint) { this.query = Objects.requireNonNull(query); this.codeFingerprint = Objects.requireNonNull(codeFingerprint); }
    public QuerySpec query() { return query; }
    public String codeFingerprint() { return codeFingerprint; }
    public int candidateBudget() { return query.candidateBudget(); }
    public boolean isCancelled() { return Thread.currentThread().isInterrupted(); }
}
