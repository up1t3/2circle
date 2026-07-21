package com.twocircle.bike.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.R

/**
 * Three-card first-run onboarding.
 *
 * Hosts in MainActivity while [OnboardingStore.completed] is false; once the user taps
 * "Get started" or "Skip", we flip the flag and the host swaps this screen out for the
 * main nav host. The flag persists across installs (DataStore lives in app data), so a
 * returning user never sees onboarding twice.
 */
private data class OnboardingStep(
    @DrawableRes val illustration: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val STEPS = listOf(
    OnboardingStep(
        illustration = R.drawable.onboarding_maps,
        title = R.string.onboarding_title_1,
        body = R.string.onboarding_body_1,
    ),
    OnboardingStep(
        illustration = R.drawable.onboarding_search,
        title = R.string.onboarding_title_2,
        body = R.string.onboarding_body_2,
    ),
    OnboardingStep(
        illustration = R.drawable.onboarding_export,
        title = R.string.onboarding_title_3,
        body = R.string.onboarding_body_3,
    ),
)

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val current = STEPS[step]
    val isLast = step == STEPS.lastIndex

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top row: step counter + Skip.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_of, step + 1, STEPS.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            TextButton(onClick = onCompleted) {
                Text(stringResource(R.string.onboarding_action_skip))
            }
        }

        // Illustration + headline/body.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(current.illustration),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(current.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(current.body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Bottom: page indicator + primary button.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STEPS.indices.forEach { i ->
                    val active = i == step
                    Box(
                        modifier = Modifier
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
            Button(
                onClick = {
                    if (isLast) onCompleted() else step++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (isLast) R.string.onboarding_action_done
                        else R.string.onboarding_action_next,
                    ),
                )
            }
        }
    }
}
