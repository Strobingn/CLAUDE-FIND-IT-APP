package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ProposedMapFeature

/**
 * Lists AI-traced historic-map features awaiting the user's decision - accepted features become
 * real persisted [com.example.data.historicmap.HistoricMapFeature] records with a real
 * terrain-agreement score; dismissed ones are simply dropped. Nothing here writes to the
 * database - it only surfaces proposals for a human to confirm, per the app's confirm-write rule.
 */
@Composable
fun HistoricMapAiFeatureReviewCard(
    proposals: List<ProposedMapFeature>,
    onAccept: (ProposedMapFeature) -> Unit,
    onDismiss: (ProposedMapFeature) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (proposals.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.testTag("historic_map_ai_review_card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "AI found ${proposals.size} possible feature${if (proposals.size == 1) "" else "s"} — review before saving",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            proposals.forEachIndexed { index, proposal ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${proposal.type.label} · ${proposal.normalizedPoints.size} points",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        proposal.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { onDismiss(proposal) },
                            modifier = Modifier.testTag("ai_feature_dismiss_$index"),
                        ) { Text("Dismiss") }
                        TextButton(
                            onClick = { onAccept(proposal) },
                            modifier = Modifier.testTag("ai_feature_accept_$index"),
                        ) { Text("Save") }
                    }
                }
            }
            if (proposals.size > 1) {
                TextButton(
                    onClick = onDismissAll,
                    modifier = Modifier.fillMaxWidth().testTag("ai_feature_dismiss_all"),
                ) { Text("Dismiss all") }
            }
        }
    }
}
