package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind;
import io.github.jichuo1.meguviz.ref.api.descriptor.TypeDescriptor;
import io.github.jichuo1.meguviz.ref.api.evidence.CodeIdentity;
import io.github.jichuo1.meguviz.ref.api.evidence.ResolutionReceipt;
import io.github.jichuo1.meguviz.ref.api.query.AccessPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReceiptJson {
    private ReceiptJson() { }
    public static byte[] encode(StoredResolution record) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schema_major", StoredResolution.SCHEMA_MAJOR);
        json.put("schema_minor", StoredResolution.SCHEMA_MINOR);
        json.put("record_kind", "exact_descriptor_hint");
        json.put("code", identity(record.identity()));
        json.put("member", member(record.descriptor()));
        json.put("access_policy", record.accessPolicy().name());
        json.put("required_modifiers", record.requiredModifiers());
        json.put("forbidden_modifiers", record.forbiddenModifiers());
        json.put("query_sha256", record.queryFingerprint());
        return BoundedJson.encode(json);
    }
    public static StoredResolution decode(byte[] bytes) {
        Map<String, Object> json = object(BoundedJson.decode(bytes));
        keys(json, "schema_major", "schema_minor", "record_kind", "code", "member", "access_policy",
                "required_modifiers", "forbidden_modifiers", "query_sha256");
        if (integer(json, "schema_major") != StoredResolution.SCHEMA_MAJOR) throw new IllegalArgumentException("unsupported schema major");
        if (integer(json, "schema_minor") != StoredResolution.SCHEMA_MINOR) throw new IllegalArgumentException("unsupported schema minor");
        if (!string(json, "record_kind").equals("exact_descriptor_hint")) throw new IllegalArgumentException("unsupported record kind");
        Map<String, Object> code = object(json.get("code"));
        keys(code, "strength", "artifacts", "loading_configuration", "rule_version", "fingerprint");
        if (!string(code, "strength").equals("STRONG")) throw new IllegalArgumentException("weak code identity");
        Map<String, String> artifacts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : object(code.get("artifacts")).entrySet()) {
            if (!(entry.getValue() instanceof String)) throw new IllegalArgumentException("artifact digest must be string");
            artifacts.put(entry.getKey(), (String) entry.getValue());
        }
        CodeIdentity identity = CodeIdentity.strong(artifacts, string(code, "loading_configuration"), string(code, "rule_version"));
        if (!identity.fingerprint().equals(string(code, "fingerprint"))) throw new IllegalArgumentException("identity digest mismatch");
        Map<String, Object> member = object(json.get("member"));
        keys(member, "kind", "owner", "name", "parameters", "type");
        Object values = member.get("parameters");
        if (!(values instanceof List)) throw new IllegalArgumentException("parameters must be array");
        List<?> parameterList = (List<?>) values;
        if (parameterList.size() > 255) throw new IllegalArgumentException("parameter count");
        String[] parameters = new String[parameterList.size()];
        for (int i = 0; i < parameters.length; i++) {
            if (!(parameterList.get(i) instanceof String)) throw new IllegalArgumentException("parameter must be descriptor string");
            parameters[i] = (String) parameterList.get(i);
        }
        MemberDescriptor descriptor = MemberDescriptor.create(MemberKind.valueOf(string(member, "kind")), string(member, "owner"),
                string(member, "name"), string(member, "type"), parameters);
        StoredResolution record = new StoredResolution(identity, descriptor, AccessPolicy.valueOf(string(json, "access_policy")),
                integer(json, "required_modifiers"), integer(json, "forbidden_modifiers"));
        if (!record.queryFingerprint().equals(string(json, "query_sha256"))) throw new IllegalArgumentException("query digest mismatch");
        return record;
    }

    /** Diagnostic export is deliberately distinct from the persistable hint envelope. */
    public static byte[] explain(ResolutionReceipt receipt) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schema_major", ResolutionReceipt.SCHEMA_MAJOR);
        json.put("record_kind", "diagnostic_only");
        json.put("query", receipt.querySummary());
        json.put("selection", receipt.selection().name());
        json.put("status", receipt.status().name());
        json.put("reason", receipt.reason());
        json.put("cause_type", receipt.causeType());
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("universe", receipt.coverage().universe());
        coverage.put("complete", receipt.coverage().complete());
        coverage.put("inspected", receipt.coverage().inspected());
        coverage.put("reason", receipt.coverage().reason());
        json.put("coverage", coverage);
        List<Object> members = new ArrayList<>();
        for (MemberDescriptor descriptor : receipt.descriptors()) members.add(member(descriptor));
        json.put("members", members);
        return BoundedJson.encode(json);
    }
    private static Map<String, Object> identity(CodeIdentity identity) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("strength", identity.strength().name()); json.put("artifacts", identity.artifacts());
        json.put("loading_configuration", identity.loadingConfiguration()); json.put("rule_version", identity.ruleVersion());
        json.put("fingerprint", identity.fingerprint()); return json;
    }
    private static Map<String, Object> member(MemberDescriptor member) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("kind", member.kind().name()); json.put("owner", member.owner()); json.put("name", member.name());
        List<String> parameters = new ArrayList<>();
        for (TypeDescriptor parameter : member.parameters()) parameters.add(parameter.value());
        json.put("parameters", parameters); json.put("type", member.type().value()); return json;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object object) {
        if (!(object instanceof Map)) throw new IllegalArgumentException("expected object");
        return (Map<String, Object>) object;
    }
    private static String string(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("expected string: " + key);
        return (String) value;
    }
    private static int integer(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof Long) || (Long) value < Integer.MIN_VALUE || (Long) value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("expected integer: " + key);
        return ((Long) value).intValue();
    }
    private static void keys(Map<String, Object> json, String... expected) {
        if (!json.keySet().equals(new java.util.HashSet<>(Arrays.asList(expected)))) throw new IllegalArgumentException("unknown or missing schema fields");
    }
}
