package io.github.jichuo1.meguviz.ref.spi;

import java.util.*;

/** Immutable installation snapshot, exclusively transferred to one CodeScope. New scopes use new snapshots. */
public final class ProviderRegistry implements AutoCloseable {
    private Map<String, CandidateProvider> providers;
    private final List<String> order;
    private boolean claimed;
    private ProviderRegistry(Map<String, CandidateProvider> providers, List<String> order) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers)); this.order = Collections.unmodifiableList(new ArrayList<>(order));
    }
    public static ProviderRegistry create(Collection<? extends CandidateProvider> candidates) { return create(candidates, Collections.emptyMap()); }
    public static ProviderRegistry create(Collection<? extends CandidateProvider> candidates, Map<String, String> explicitCapabilityOwners) {
        Map<String, CandidateProvider> providers = new TreeMap<>();
        Map<String, List<String>> capabilities = new TreeMap<>();
        for (CandidateProvider provider : candidates) {
            ProviderDescriptor d = provider.descriptor();
            if (d.spiMajor() != 1) throw new IllegalArgumentException("unsupported SPI major: " + d.spiMajor());
            if (providers.put(d.id(), provider) != null) throw new IllegalArgumentException("duplicate provider: " + d.id());
            if (!d.exclusiveCapability().isEmpty()) capabilities.computeIfAbsent(d.exclusiveCapability(), ignored -> new ArrayList<>()).add(d.id());
        }
        for (Map.Entry<String, List<String>> capability : capabilities.entrySet()) {
            if (capability.getValue().size() > 1) {
                String selected = explicitCapabilityOwners.get(capability.getKey());
                if (selected == null || !capability.getValue().contains(selected)) throw new IllegalArgumentException("conflicting capability: " + capability.getKey());
                for (String id : capability.getValue()) if (!id.equals(selected)) providers.remove(id);
            }
        }
        List<String> order = new ArrayList<>();
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String id : providers.keySet()) visit(id, providers, visiting, visited, order);
        return new ProviderRegistry(providers, order);
    }
    private static void visit(String id, Map<String, CandidateProvider> providers, Set<String> visiting, Set<String> visited, List<String> order) {
        if (visited.contains(id)) return;
        CandidateProvider provider = providers.get(id);
        if (provider == null) throw new IllegalArgumentException("missing provider dependency: " + id);
        if (!visiting.add(id)) throw new IllegalArgumentException("provider dependency cycle: " + id);
        List<String> dependencies = new ArrayList<>(provider.descriptor().dependencies()); Collections.sort(dependencies);
        for (String dependency : dependencies) visit(dependency, providers, visiting, visited, order);
        visiting.remove(id); visited.add(id); order.add(id);
    }
    public synchronized void claim() { if (claimed || providers == null) throw new IllegalStateException("registry already transferred or closed"); claimed = true; }
    public synchronized CandidateProvider provider(String id) { return providers == null ? null : providers.get(id); }
    public List<String> installationOrder() { return order; }
    @Override public void close() {
        Map<String, CandidateProvider> previous;
        synchronized (this) { previous = providers; providers = null; }
        if (previous == null) return;
        RuntimeException failure = null;
        for (int i = order.size() - 1; i >= 0; i--) {
            try { previous.get(order.get(i)).close(); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }
}
