package io.github.jichuo1.meguviz.ref.spi;

import java.util.*;

/** SPI metadata. Providers execute synchronously on the calling thread and are closed by their owning scope. */
public final class ProviderDescriptor {
    private final String id, implementationVersion, capability;
    private final int major, minor;
    private final List<String> dependencies;
    public ProviderDescriptor(String id, String implementationVersion, int major, int minor, String exclusiveCapability, List<String> dependencies) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]{1,128}")) throw new IllegalArgumentException("provider ID");
        if (implementationVersion == null || implementationVersion.isEmpty() || implementationVersion.length() > 128) throw new IllegalArgumentException("provider version");
        if (major < 0 || minor < 0) throw new IllegalArgumentException("SPI version");
        this.id = id; this.implementationVersion = implementationVersion; this.major = major; this.minor = minor;
        this.capability = Objects.requireNonNull(exclusiveCapability); this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
    }
    public String id() { return id; }
    public String implementationVersion() { return implementationVersion; }
    public int spiMajor() { return major; }
    public int spiMinor() { return minor; }
    public String exclusiveCapability() { return capability; }
    public List<String> dependencies() { return dependencies; }
    public String threadConstraint() { return "CALLER_THREAD"; }
    public String closeOwner() { return "CODE_SCOPE"; }
}
