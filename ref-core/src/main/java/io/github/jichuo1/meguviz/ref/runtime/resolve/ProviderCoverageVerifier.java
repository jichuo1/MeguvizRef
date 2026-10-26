package io.github.jichuo1.meguviz.ref.runtime.resolve;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import io.github.jichuo1.meguviz.ref.api.query.*;
import java.lang.reflect.*;
import java.util.*;

/** Independent cold-path visible-domain replay. A provider's complete flag alone is never a uniqueness proof. */
final class ProviderCoverageVerifier {
    static boolean verifies(QuerySpec query, Class<?> owner, Set<Member> provided) {
        Set<Member> expected = new HashSet<>();
        Class<?>[] requiredParameters = query.parameterClasses(), argumentTypes = query.argumentClasses();
        int depth = 0, inspected = 0;
        try {
            for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
                if (++depth > query.depthLimit()) return false;
                boolean javaPublic = query.hierarchy() == Hierarchy.JAVA_PUBLIC;
                Member[] domain;
                if (query.kind() == MemberKind.METHOD) domain = javaPublic ? current.getMethods() : current.getDeclaredMethods();
                else if (query.kind() == MemberKind.FIELD) domain = javaPublic ? current.getFields() : current.getDeclaredFields();
                else domain = current.getDeclaredConstructors();
                for (Member member : domain) {
                    if (Thread.currentThread().isInterrupted() || ++inspected > query.candidateBudget()) return false;
                    String name = member instanceof Constructor<?> ? "<init>" : member.getName();
                    if (query.name() != null && !query.name().equals(name)) continue;
                    if (query.staticOnly() != null && query.staticOnly() != Modifier.isStatic(member.getModifiers())) continue;
                    if (query.synthetic() != null && query.synthetic() != member.isSynthetic()) continue;
                    if (query.bridge() != null && query.bridge() != (member instanceof Method && ((Method) member).isBridge())) continue;
                    Class<?>[] parameters;
                    Class<?> type;
                    if (member instanceof Method) { parameters = ((Method) member).getParameterTypes(); type = ((Method) member).getReturnType(); }
                    else if (member instanceof Constructor<?>) { parameters = ((Constructor<?>) member).getParameterTypes(); type = void.class; }
                    else { parameters = new Class<?>[0]; type = ((Field) member).getType(); }
                    if (query.parameterCount() >= 0 && parameters.length != query.parameterCount()) continue;
                    if (requiredParameters != null && !Arrays.equals(parameters, requiredParameters)) continue;
                    if (query.returnClass() != null && type != query.returnClass()) continue;
                    if (argumentTypes != null) {
                        if (parameters.length != argumentTypes.length) continue;
                        boolean compatible = true;
                        for (int i = 0; i < parameters.length; i++) if (!query.compatibility().accepts(parameters[i], argumentTypes[i])) { compatible = false; break; }
                        if (!compatible) continue;
                    }
                    if (query.exactDescriptor() != null && !query.exactDescriptor().equals(MemberDescriptor.from(member))) continue;
                    expected.add(member);
                }
                if (query.hierarchy() != Hierarchy.SUPERCLASSES) break;
            }
        } catch (LinkageError | SecurityException failure) { return false; }
        return expected.equals(provided);
    }
}
