package io.github.limuyang2.router.demo

import androidx.compose.runtime.mutableStateOf

/**
 * Result presentation owned by a route instance, rather than one Composition.
 * A pending callback and a recreated page must observe the same state objects.
 * Each route creates its own holder so repeated destinations remain independent.
 */
class DemoResultState {
    val result = mutableStateOf<String?>(null)
    val value = mutableStateOf<String?>(null)
}
