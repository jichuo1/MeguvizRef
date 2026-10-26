package io.github.jichuo1.meguviz.ref.spi;
public interface CandidateProvider extends AutoCloseable {
    ProviderDescriptor descriptor();
    CandidateBatch discover(CandidateRequest request) throws Exception;
    @Override void close();
}
