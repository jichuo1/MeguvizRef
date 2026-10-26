-keep class example.meguviz.ReflectTarget {
    public static java.lang.String message(java.lang.String);
}
# Instrumentation references these public adapter/API entries in a separately compiled APK.
-keep class io.github.jichuo1.meguviz.ref.android.AndroidEnvironment {
    public static *** capture(android.content.Context);
    public int api();
    public int targetSdk();
    public java.util.List codePaths();
    public java.lang.String artImplementation();
}
# Exact public entry points used by the separate benchmark instrumentation APK.
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity {
    public static *** strong(java.util.Map,java.lang.String,java.lang.String);
    public java.lang.String fingerprint();
}
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor {
    public static *** method(java.lang.String,java.lang.String,java.lang.String,java.lang.String[]);
    public static *** from(java.lang.reflect.Member);
    public java.lang.String name();
}
-keep class io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind { public static final *** METHOD; }
-keep class io.github.jichuo1.meguviz.ref.api.query.Selection { public static final *** ALL; }
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.query.QuerySpec {
    public static *** exact(io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor);
    public static *** builder(java.lang.Class,io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind);
}
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.query.QuerySpec$Builder {
    public *** selection(io.github.jichuo1.meguviz.ref.api.query.Selection);
    public *** provider(java.lang.String);
    public *** build();
}
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.result.ResolutionResult {
    public java.util.List members();
    public *** status();
    public *** requireSingle();
    public *** receipt();
}
-keep class io.github.jichuo1.meguviz.ref.api.result.ResolutionStatus { public static final *** ALL_MATCHES; }
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.result.ResolvedMember { public *** descriptor(); }
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.api.evidence.ResolutionReceipt { public *** coverage(); }
-keep class io.github.jichuo1.meguviz.ref.api.result.Coverage { public boolean complete(); }
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.scope.CodeScope {
    public static *** open(java.lang.ClassLoader);
    public void installProviders(io.github.jichuo1.meguviz.ref.spi.ProviderRegistry,io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity);
    public *** resolve(io.github.jichuo1.meguviz.ref.api.query.QuerySpec);
    public *** bindMethod(io.github.jichuo1.meguviz.ref.api.result.ResolvedMember);
    public java.lang.reflect.Member exportRaw(io.github.jichuo1.meguviz.ref.api.result.ResolvedMember);
    public void advanceCodeEpoch(java.lang.String);
    public void close();
}
-keep class io.github.jichuo1.meguviz.ref.runtime.invoke.MethodPlan { public java.lang.Object invoke(java.lang.Object,java.lang.Object[]); }
-keep class io.github.jichuo1.meguviz.ref.spi.ProviderDescriptor { public <init>(java.lang.String,java.lang.String,int,int,java.lang.String,java.util.List); }
-keep,includedescriptorclasses class io.github.jichuo1.meguviz.ref.spi.ProviderRegistry { public static *** create(java.util.Collection); }
-keep,includedescriptorclasses interface io.github.jichuo1.meguviz.ref.spi.CandidateProvider {
    public *** descriptor();
    public *** discover(io.github.jichuo1.meguviz.ref.spi.CandidateRequest);
    public void close();
}
-keep class io.github.jichuo1.meguviz.ref.spi.CandidateBatch { public <init>(java.util.List,java.lang.String,java.lang.String,boolean); }
