package io.github.jichuo1.meguviz.ref.runtime.invoke;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.result.ResolvedMember;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;

public final class FieldPlan {
    private final CodeScope scope;
    private final ResolvedMember member;
    public FieldPlan(CodeScope scope, ResolvedMember member) { this.scope = scope; this.member = member; }
    public MemberDescriptor descriptor() { return member.descriptor(); }
    public Object get(Object receiver) throws IllegalAccessException { return scope.getField(member, receiver); }
    public void set(Object receiver, Object value) throws IllegalAccessException { scope.setField(member, receiver, value); }
}
