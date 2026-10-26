package io.github.jichuo1.meguviz.ref.runtime.invoke;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.result.ResolvedMember;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;
import java.lang.reflect.InvocationTargetException;

public final class ConstructorPlan {
    private final CodeScope scope;
    private final ResolvedMember member;
    public ConstructorPlan(CodeScope scope, ResolvedMember member) { this.scope = scope; this.member = member; }
    public MemberDescriptor descriptor() { return member.descriptor(); }
    public Object newInstance(Object[] arguments) throws IllegalAccessException, InvocationTargetException, InstantiationException { return scope.construct(member, arguments); }
}
