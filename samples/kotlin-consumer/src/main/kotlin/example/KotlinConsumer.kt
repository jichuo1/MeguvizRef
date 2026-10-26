package example

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec
import io.github.jichuo1.meguviz.ref.scope.CodeScope
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    check(Runtime.version().feature() == 17)
    CodeScope.open(String::class.java.classLoader).use { scope ->
        val query = QuerySpec.exact(MemberDescriptor.method("java.lang.String", "substring", "Ljava/lang/String;", "I"))
        val plan = scope.bindMethod(scope.resolve(query).requireSingle())
        check(plan.invoke("Meguviz", arrayOf(4)) == "viz")
    }
    Files.createDirectories(Path.of("build"))
    Files.writeString(Path.of("build/kotlin-consumer.json"), "{\"tests\":2,\"java\":17,\"status\":\"passed\"}")
    println("Kotlin independent consumer TESTS=2")
}
