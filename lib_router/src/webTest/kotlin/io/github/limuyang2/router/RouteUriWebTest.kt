package io.github.limuyang2.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RouteUriWebTest {
    @Test
    fun customSchemePreservesRepeatedAndEncodedParameters() {
        val uri = assertNotNull("test://modules/help?topic=%E5%95%86%E5%93%81+42&topic=a%2Bb&empty&encoded%20key=yes#section".toUriOrNull())
        assertEquals("test", uri.scheme)
        assertEquals("modules", uri.host)
        assertEquals("/help", uri.path)
        assertEquals(listOf("商品 42", "a+b"), uri.getQueryParameters("topic"))
        assertEquals("商品 42", uri.getQueryParameter("topic"))
        assertEquals("", uri.getQueryParameter("empty"))
        assertEquals("yes", uri.getQueryParameter("encoded key"))
        assertNull(uri.getQueryParameter("missing"))
    }

    @Test
    fun buildUponKeepsAuthorityPathAndFragmentWhileReplacingQuery() {
        val original = assertNotNull("https://example.com:8443/a%20b?old=1#details".toUriOrNull())
        val uri = assertNotNull(original.buildUpon().clearQuery().appendQueryParameter("name", "商品 +&").build())
        assertEquals("example.com", uri.host)
        assertEquals("/a b", uri.path)
        assertEquals("商品 +&", uri.getQueryParameter("name"))
        assertNull(uri.getQueryParameter("old"))
        assertEquals("https://example.com:8443/a%20b?name=%E5%95%86%E5%93%81%20%2B%26#details", uri.toString())
    }

    @Test
    fun builderEncodesPathAndQueryWithoutChangingRouteIdentity() {
        val uri = assertNotNull(RouteUri.Builder().scheme("test").authority("modules")
            .path("帮助").appendQueryParameter("a b", "1=2&3").appendQueryParameter("a b", null).build())
        assertEquals("/帮助", uri.path)
        assertEquals(listOf("1=2&3", ""), uri.getQueryParameters("a b"))
        assertEquals(setOf("a b"), uri.getQueryParameterNames())
    }

    @Test
    fun relativePathsDoNotAcquireBrowserLocationOrHttpScheme() {
        val uri = assertNotNull("/help?topic=account".toUriOrNull())
        assertNull(uri.scheme)
        assertNull(uri.host)
        assertEquals("/help", uri.path)
        assertEquals("account", uri.getQueryParameter("topic"))
    }

    @Test
    fun invalidUrlsReturnNull() {
        listOf("", " ", "test://modules/a b", "test://modules/help?x=%", "test://modules/help?x=%ZZ", "1test://modules/help")
            .forEach { assertNull(it.toUriOrNull(), it) }
    }
}
