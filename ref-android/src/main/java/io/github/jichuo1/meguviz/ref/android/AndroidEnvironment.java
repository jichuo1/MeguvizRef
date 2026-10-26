package io.github.jichuo1.meguviz.ref.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A copied value snapshot. It never owns a Context, ClassLoader, or APK handle. */
public final class AndroidEnvironment {
    private final int api;
    private final int targetSdk;
    private final List<String> codePaths;
    private final List<String> abis;
    private AndroidEnvironment(int api, int targetSdk, List<String> paths, List<String> abis) {
        this.api = api;
        this.targetSdk = targetSdk;
        this.codePaths = Collections.unmodifiableList(new ArrayList<>(paths));
        this.abis = Collections.unmodifiableList(new ArrayList<>(abis));
    }
    public static AndroidEnvironment capture(Context context) {
        ApplicationInfo info = context.getApplicationInfo();
        List<String> paths = new ArrayList<>();
        if (info.sourceDir != null) paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) Collections.addAll(paths, info.splitSourceDirs);
        List<String> abis = new ArrayList<>();
        Collections.addAll(abis, Build.SUPPORTED_ABIS);
        return new AndroidEnvironment(Build.VERSION.SDK_INT, info.targetSdkVersion, paths, abis);
    }
    public int api() { return api; }
    public int targetSdk() { return targetSdk; }
    public List<String> codePaths() { return codePaths; }
    public List<String> abis() { return abis; }
    public String artImplementation() { return "unknown"; }
    public String codeIdentityStrength() { return "unverified-path-hints"; }
}
