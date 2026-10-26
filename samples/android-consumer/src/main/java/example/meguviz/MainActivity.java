package example.meguviz;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        try (CodeScope scope = CodeScope.open(getClassLoader())) {
            var descriptor = MemberDescriptor.method("example.meguviz.ReflectTarget", "message", "Ljava/lang/String;", "Ljava/lang/String;");
            var plan = scope.bindMethod(scope.resolve(QuerySpec.exact(descriptor)).requireSingle());
            view.setText((String) plan.invoke(null, new Object[]{"Android"}));
        } catch (Exception failure) {
            view.setText("Contract failed: " + failure.getClass().getSimpleName());
        }
        setContentView(view);
    }
}
