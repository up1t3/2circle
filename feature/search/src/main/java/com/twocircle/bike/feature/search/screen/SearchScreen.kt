package com.twocircle.bike.feature.search.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.common.format.Format
import com.twocircle.bike.feature.search.model.ScoredResult

/**
 * Offline search screen.
 *
 * Renders a debounced search field + ranked results list. Each row shows name, kind, and
 * distance (when an anchor exists). Tapping a result navigates to the map centred on it;
 * the "add as waypoint" affordance is added by the routing feature (Step 5).
 *
 * States branch on [SearchUiState] — empty / no-region / error get tailored messaging.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onResultSelected: (ScoredResult) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search places, springs, passes…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
        )

        when (val s = state) {
            SearchUiState.Idle -> Hint("Type a place name to search offline.")
            SearchUiState.Searching -> Loading()
            is SearchUiState.Results -> ResultList(s.items, onResultSelected)
            SearchUiState.Empty -> Hint("No matches found.")
            SearchUiState.NoRegion -> Hint(
                "Download a region in the Regions tab to enable offline search.",
            )
            is SearchUiState.Error -> Hint("Search error: ${s.failure.javaClass.simpleName}")
        }
    }
}

@Composable
private fun ResultList(items: List<ScoredResult>, onSelect: (ScoredResult) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.hit.rowId }) { result ->
            ResultRow(result, onClick = { onSelect(result) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun ResultRow(result: ScoredResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = result.hit.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = result.hit.kind.osmValue,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            result.distanceKm?.let { km ->
                Text(
                    text = Format.distance(km * 1000.0),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (result.hit.population > 0) {
                Text(
                    text = "pop ${result.hit.population}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Hint(text: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        )
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 12.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}
