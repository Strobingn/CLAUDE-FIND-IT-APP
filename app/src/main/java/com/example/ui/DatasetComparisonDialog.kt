package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.DatasetComparison
import com.example.analysis.DatasetComparisonResult
import com.example.data.local.AnalyzedDatasetEntity
import com.example.data.local.SavedTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cross-references two previously-analyzed terrain datasets by real-world distance between their
 * saved targets. Candidates that agree between two independent analyses of overlapping ground are
 * stronger evidence than either alone; this only reports coincidence, it never fabricates a score.
 */
@Composable
fun DatasetComparisonDialog(
    datasets: List<AnalyzedDatasetEntity>,
    onDismiss: () -> Unit,
) {
    var firstKey by remember { mutableStateOf(datasets.getOrNull(0)?.datasetKey) }
    var secondKey by remember { mutableStateOf(datasets.getOrNull(1)?.datasetKey) }
    val first = datasets.firstOrNull { it.datasetKey == firstKey }
    val second = datasets.firstOrNull { it.datasetKey == secondKey }
    val result = if (first != null && second != null && first.datasetKey != second.datasetKey) {
        remember(first.datasetKey, second.datasetKey) { DatasetComparison.compare(first, second) }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compare datasets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cross-references saved targets between two analyzed datasets by real-world distance " +
                        "(within ${DatasetComparison.MATCH_DISTANCE_METERS.toInt()} m). Agreement across " +
                        "independent analyses of overlapping ground is stronger evidence than either alone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DatasetPicker("First dataset", datasets, firstKey) { firstKey = it }
                DatasetPicker("Second dataset", datasets, secondKey) { secondKey = it }

                when {
                    first == null || second == null -> Text(
                        "Pick two analyzed datasets to compare.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    first.datasetKey == second.datasetKey -> Text(
                        "Pick two different datasets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    result != null && !result.bothGeoreferenced -> Text(
                        "At least one of these datasets has no geographic coordinates, so targets can't be " +
                            "matched by real-world location.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    result != null -> ComparisonSummary(result)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DatasetPicker(
    label: String,
    datasets: List<AnalyzedDatasetEntity>,
    selectedKey: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = datasets.firstOrNull { it.datasetKey == selectedKey }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selected?.let { "${it.displayName} · ${dateFormat.format(Date(it.analyzedAtMillis))}" } ?: "Select a dataset",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                datasets.forEach { dataset ->
                    DropdownMenuItem(
                        text = { Text("${dataset.displayName} · ${dateFormat.format(Date(dataset.analyzedAtMillis))}") },
                        onClick = {
                            onSelected(dataset.datasetKey)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSummary(result: DatasetComparisonResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${result.agreements.size} agree · ${result.uniqueToFirst.size} unique to ${result.firstName} · " +
                "${result.uniqueToSecond.size} unique to ${result.secondName}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (result.agreements.isNotEmpty()) {
                item { SectionLabel("Agreements (both datasets)") }
                items(result.agreements, key = { "agree-${it.fromFirst?.xPercent}-${it.fromFirst?.yPercent}-${it.fromSecond?.xPercent}" }) { match ->
                    val first = match.fromFirst
                    val second = match.fromSecond
                    if (first != null && second != null) {
                        MatchCard(first, second, match.distanceMeters)
                    }
                }
            }
            if (result.uniqueToFirst.isNotEmpty()) {
                item { SectionLabel("Only in ${result.firstName}") }
                items(result.uniqueToFirst, key = { "u1-${it.xPercent}-${it.yPercent}-${it.type}" }) { target ->
                    UniqueCard(target)
                }
            }
            if (result.uniqueToSecond.isNotEmpty()) {
                item { SectionLabel("Only in ${result.secondName}") }
                items(result.uniqueToSecond, key = { "u2-${it.xPercent}-${it.yPercent}-${it.type}" }) { target ->
                    UniqueCard(target)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun MatchCard(first: SavedTarget, second: SavedTarget, distanceMeters: Double?) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "${first.type.label} (${(first.score * 100).toInt()}%) ↔ ${second.type.label} (${(second.score * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            if (distanceMeters != null) {
                Text(
                    "${"%.1f".format(distanceMeters)} m apart",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UniqueCard(target: SavedTarget) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${target.type.label} · ${(target.score * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
        )
    }
}
