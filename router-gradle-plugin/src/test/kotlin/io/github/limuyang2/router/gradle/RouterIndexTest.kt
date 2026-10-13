package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouterIndexTest {
    @Test fun `index has stable identity and deterministic encoding`() {
        val index = RouterIndex("com.example:home", listOf("sample.Z", "sample.A"))
        assertEquals(index.suffix, RouterIndex("com.example:home", emptyList()).suffix)
        assertEquals(24, index.suffix.length)
        assertNotEquals(index.suffix, RouterIndex("com.example:other", emptyList()).suffix)
        assertNotEquals(index.resourcePath, RouterIndex("com.example:other", emptyList()).resourcePath)
        assertEquals(listOf("sample.A", "sample.Z"), RouterIndex.decode(index.encode()).registries)
        assertEquals(index.encode(), RouterIndex.decode(index.encode()).encode())
    }

    @Test fun `incompatible and corrupt indices are rejected`() {
        val valid = RouterIndex("example:home", listOf("sample.Registry")).encode()
        listOf(
            valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            valid.replace("\"registryApiVersion\": 1", "\"registryApiVersion\": 2"),
            valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
            valid.replace("example:home", "../home"),
            valid.replace("sample.Registry", "sample.class"),
            valid.replace("\"sample.Registry\"", "\"sample.Registry\", \"sample.Registry\""),
            "[]", "{}", valid + "{}",
        ).forEach { invalid ->
            assertThrows("Accepted invalid index: $invalid", Exception::class.java) { RouterIndex.decode(invalid) }
        }
    }
}
