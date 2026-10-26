package example.meguviz;

import android.app.Instrumentation;
import android.os.Bundle;
import io.github.jichuo1.meguviz.ref.android.AndroidEnvironment;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class ContractInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int count = 0;
        try {
            CodeScope scope = CodeScope.open(getTargetContext().getClassLoader());
            var spec = QuerySpec.exact(MemberDescriptor.method("example.meguviz.ReflectTarget", "message", "Ljava/lang/String;", "Ljava/lang/String;"));
            var plan = scope.bindMethod(scope.resolve(spec).requireSingle());
            require("MeguvizRef test".equals(plan.invoke(null, new Object[]{"test"})), "release reflective invocation"); count++;
            scope.close();
            boolean stale = false;
            try { plan.invoke(null, new Object[]{"stale"}); } catch (IllegalStateException expected) { stale = true; }
            require(stale, "closed plan"); count++;
            AndroidEnvironment env = AndroidEnvironment.capture(getTargetContext());
            require(env.api() >= 27 && env.targetSdk() == 37 && !env.codePaths().isEmpty(), "platform snapshot"); count++;
            require("unknown".equals(env.artImplementation()), "no inferred ART version"); count++;
            byte[] dex;
            try (var stream = getContext().getAssets().open("classes.dex"); var bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = stream.read(buffer)) != -1) bytes.write(buffer, 0, length);
                dex = bytes.toByteArray();
            }
            ClassLoader first = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), getTargetContext().getClassLoader());
            ClassLoader second = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), getTargetContext().getClassLoader());
            Class<?> firstType = first.loadClass("fixture.SameName");
            Class<?> secondType = second.loadClass("fixture.SameName");
            require(firstType != secondType, "same-name loader identity");
            try (CodeScope isolated = CodeScope.open(first)) {
                var isolatedQuery = QuerySpec.exact(MemberDescriptor.method("fixture.SameName", "read", "I"));
                var isolatedPlan = isolated.bindMethod(isolated.resolve(isolatedQuery).requireSingle());
                require(Integer.valueOf(7).equals(isolatedPlan.invoke(firstType.getConstructor().newInstance(), new Object[0])), "fixture invocation");
                boolean wrongReceiver = false;
                try { isolatedPlan.invoke(secondType.getConstructor().newInstance(), new Object[0]); }
                catch (IllegalArgumentException expected) { wrongReceiver = true; }
                require(wrongReceiver, "cross-loader receiver rejection"); count++;
                isolated.advanceCodeEpoch("fixture reload notification");
                boolean oldEpoch = false;
                try { isolatedPlan.invoke(firstType.getConstructor().newInstance(), new Object[0]); }
                catch (IllegalStateException expected) { oldEpoch = true; }
                require(oldEpoch, "epoch invalidates previous plan"); count++;
            }
            result.putString("stream", "\nOK (" + count + " tests)\n");
            result.putInt("meguviz_tests", count);
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAILURES!!! " + failure + "\n");
            result.putInt("meguviz_tests", count);
            finish(0, result);
        }
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
