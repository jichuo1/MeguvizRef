package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Independent public-reflection enumeration for finite fixtures; deliberately does not use the core selector. */
public final class IndependentVerifier {
    public enum Status { VERIFIED, MISMATCH, UNAVAILABLE }
    private IndependentVerifier() { }
    public static Status verify(ClassLoader loader, MemberDescriptor descriptor, int required, int forbidden) {
        try {
            Class<?> owner = Class.forName(descriptor.owner(), false, loader);
            Member[] candidates;
            if (descriptor.kind() == MemberKind.METHOD) candidates = owner.getDeclaredMethods();
            else if (descriptor.kind() == MemberKind.FIELD) candidates = owner.getDeclaredFields();
            else candidates = owner.getDeclaredConstructors();
            for (Member candidate : candidates) {
                if (same(candidate, descriptor)) {
                    return (candidate.getModifiers() & required) == required && (candidate.getModifiers() & forbidden) == 0
                            ? Status.VERIFIED : Status.MISMATCH;
                }
            }
            return Status.MISMATCH;
        } catch (ClassNotFoundException | LinkageError | SecurityException unavailable) { return Status.UNAVAILABLE; }
    }
    /** Checks provider completeness against a caller-owned finite fixture universe, without accepting its complete flag. */
    public static Status verifyFiniteUniverse(Class<?> fixture, MemberKind kind, List<MemberDescriptor> expected) {
        try {
            Member[] candidates = kind == MemberKind.METHOD ? fixture.getDeclaredMethods()
                    : kind == MemberKind.FIELD ? fixture.getDeclaredFields() : fixture.getDeclaredConstructors();
            if (candidates.length != expected.size()) return Status.MISMATCH;
            List<MemberDescriptor> remaining = new ArrayList<>(expected);
            for (Member candidate : candidates) {
                int matching = -1;
                for (int i = 0; i < remaining.size(); i++) if (same(candidate, remaining.get(i))) { matching = i; break; }
                if (matching == -1) return Status.MISMATCH;
                remaining.remove(matching);
            }
            return remaining.isEmpty() ? Status.VERIFIED : Status.MISMATCH;
        } catch (LinkageError | SecurityException unavailable) { return Status.UNAVAILABLE; }
    }
    private static boolean same(Member member, MemberDescriptor expected) {
        if (!member.getDeclaringClass().getName().equals(expected.owner())) return false;
        String name = member instanceof Constructor ? "<init>" : member.getName();
        if (!name.equals(expected.name())) return false;
        Class<?>[] parameters;
        Class<?> result;
        if (member instanceof Method) {
            if (expected.kind() != MemberKind.METHOD) return false;
            parameters = ((Method) member).getParameterTypes(); result = ((Method) member).getReturnType();
        } else if (member instanceof Field) {
            if (expected.kind() != MemberKind.FIELD) return false;
            parameters = new Class<?>[0]; result = ((Field) member).getType();
        } else {
            if (expected.kind() != MemberKind.CONSTRUCTOR) return false;
            parameters = ((Constructor<?>) member).getParameterTypes(); result = void.class;
        }
        if (!type(result).equals(expected.type().value()) || parameters.length != expected.parameters().size()) return false;
        for (int i = 0; i < parameters.length; i++) if (!type(parameters[i]).equals(expected.parameters().get(i).value())) return false;
        return true;
    }
    private static String type(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }
}
