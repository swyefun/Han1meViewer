package com.yenaly.han1meviewer.ui.screen.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import org.mozilla.geckoview.GeckoRuntime


@Composable
fun CloudflareScreen(
    runtime: GeckoRuntime,
    url: String,
    userAgent: String,
    progress: Int,
    tipText: String,
    onClose: () -> Unit,
    onProgress: (Int) -> Unit,
) {
    HanimeScaffold(
        title = stringResource(R.string.complete_cloudflare_verification),
        onBack = onClose,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GeckoCloudflareView(
                runtime = runtime,
                url = url,
                userAgent = userAgent,
                modifier = Modifier.fillMaxSize(),
                onProgress = onProgress,
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    strokeCap = StrokeCap.Round,
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = tipText,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CloudflareScreenPreview() {
    // Preview 无真实 GeckoRuntime，仅展示空骨架；运行期由 Activity 提供内核。
    ComponentPreview {
        Box(modifier = Modifier.fillMaxSize())
    }
}
