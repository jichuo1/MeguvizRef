package io.github.jichuo1.meguviz.ref.runtime.resolve;

import io.github.jichuo1.meguviz.ref.api.descriptor.*;
import java.lang.reflect.*;

/** A private signature snapshot; callers cannot mutate the stored parameter array. */
public final class MethodSchema {
    private final Member member;
    private final Class<?>[] parameters;
    private final Class<?> type;
    private final MemberDescriptor descriptor;
    public MethodSchema(Member member) {
        this.member = member;
        if (member instanceof Method) {
            parameters = ((Method) member).getParameterTypes(); type = ((Method) member).getReturnType();
        } else if (member instanceof Constructor<?>) {
            parameters = ((Constructor<?>) member).getParameterTypes(); type = void.class;
        } else { parameters = new Class<?>[0]; type = ((Field) member).getType(); }
        String[] types = new String[parameters.length];
        for (int i = 0; i < types.length; i++) types[i] = TypeDescriptor.fromClass(parameters[i]).value();
        MemberKind kind = member instanceof Method ? MemberKind.METHOD : member instanceof Field ? MemberKind.FIELD : MemberKind.CONSTRUCTOR;
        descriptor = MemberDescriptor.create(kind, member.getDeclaringClass().getName(), kind == MemberKind.CONSTRUCTOR ? "<init>" : member.getName(), TypeDescriptor.fromClass(type).value(), types);
    }
    public Class<?>[] parameterTypes() { return parameters.clone(); }
    public Class<?> type() { return type; }
    public MemberDescriptor descriptor() { return descriptor; }
    public Member rawMember() { return member; }
    boolean parametersEqual(Class<?>[] requested) {
        if (requested.length != parameters.length) return false;
        for (int i = 0; i < parameters.length; i++) if (parameters[i] != requested[i]) return false;
        return true;
    }
    boolean argumentsMatch(Class<?>[] arguments, io.github.jichuo1.meguviz.ref.api.query.CompatibilityPolicy policy) {
        if (arguments.length != parameters.length) return false;
        for (int i = 0; i < parameters.length; i++) if (!policy.accepts(parameters[i], arguments[i])) return false;
        return true;
    }
}
