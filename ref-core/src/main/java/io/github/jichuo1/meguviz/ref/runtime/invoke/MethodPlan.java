package io.github.jichuo1.meguviz.ref.runtime.invoke;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.result.ResolvedMember;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import java.lang.reflect.InvocationTargetException;

public final class MethodPlan {
    private final CodeScope scope;
    private final ResolvedMember member;
    public MethodPlan(CodeScope scope, ResolvedMember member) { this.scope = scope; this.member = member; }
    public MemberDescriptor descriptor() { return member.descriptor(); }
    public Object invoke(Object receiver, Object[] arguments) throws IllegalAccessException, InvocationTargetException { return scope.invokeMethod(member, receiver, arguments); }
}
