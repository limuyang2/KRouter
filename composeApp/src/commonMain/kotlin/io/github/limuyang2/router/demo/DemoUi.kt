package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.demo.feature.shared.FeatureAction
import io.github.limuyang2.router.demo.feature.shared.FeatureCard
import io.github.limuyang2.router.demo.feature.shared.FeatureScreen
import io.github.limuyang2.router.demo.feature.shared.FeatureValue

@Composable
fun DemoScreen(eyebrow: String, title: String, subtitle: String, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null, headerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit) {
    FeatureScreen(title = title, onBack = onBack, eyebrow = eyebrow, subtitle = subtitle,
        modifier = modifier, headerContent = headerContent) { content() }
}

@Composable
fun DemoInfoCard(title: String, body: String, modifier: Modifier = Modifier, content: (@Composable () -> Unit)? = null) {
    FeatureCard(title, body, modifier, content)
}

@Composable
fun DemoActionButton(title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FeatureAction(title, description, onClick, modifier)
}

@Composable
fun DemoBulletRow(label: String, value: String) {
    FeatureValue(label, value)
}

@Composable
fun DemoSectionSpacer() {
    Spacer(Modifier.height(4.dp))
}
