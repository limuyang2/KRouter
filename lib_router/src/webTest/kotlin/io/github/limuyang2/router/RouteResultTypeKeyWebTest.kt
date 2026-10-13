package io.github.limuyang2.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RouteResultTypeKeyWebTest {
    private object First {
        class Result
    }

    private object Second {
        class Result
    }

    @Test
    fun equallyNamedResultTypesHaveDistinctStableConsumerSlots() {
        val first = buildRouteResultSlotId("result", First.Result::class)
        assertEquals(first, buildRouteResultSlotId("result", First.Result::class))
        assertNotEquals(first, buildRouteResultSlotId("result", Second.Result::class))
        assertNotEquals(first, buildRouteResultSlotId("other", First.Result::class))
    }
}
