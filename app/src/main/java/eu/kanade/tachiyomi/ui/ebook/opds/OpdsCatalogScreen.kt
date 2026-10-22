// KMK -->
package eu.kanade.tachiyomi.ui.ebook.opds

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsCatalogScreen(
    servers: List<OpdsServer>,
    onAddServer: (OpdsServer) -> Unit,
    onFetchFeed: suspend (OpdsServer, String) -> Result<OpdsFeed>,
    onDownloadBook: suspend (OpdsServer, OpdsEntry, (Float) -> Unit) -> Result<File>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedServerIndex by remember { mutableStateOf(0) }
    val currentServer = servers.getOrNull(selectedServerIndex) ?: servers.firstOrNull()

    // Navigation breadcrumb stack for drilling into OPDS subcategories
    val navHistory = remember { mutableStateListOf<Pair<String, String>>() } // Pair<Title, Url>
    var currentUrl by remember(currentServer) { mutableStateOf(currentServer?.url.orEmpty()) }

    var currentFeed by remember { mutableStateOf<OpdsFeed?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun loadFeed(server: OpdsServer, url: String) {
        scope.launch {
            loading = true
            errorMessage = null
            onFetchFeed(server, url)
                .onSuccess { feed ->
                    currentFeed = feed
                }
                .onFailure { error ->
                    errorMessage = error.localizedMessage ?: "Failed to connect to OPDS server"
                }
            loading = false
        }
    }

    LaunchedEffect(currentServer, currentUrl) {
        if (currentServer != null && currentUrl.isNotBlank()) {
            loadFeed(currentServer, currentUrl)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = navHistory.lastOrNull()?.first ?: currentFeed?.title ?: currentServer?.name ?: "OPDS Catalog",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (navHistory.isNotEmpty()) {
                                navHistory.removeAt(navHistory.lastIndex)
                                currentUrl = navHistory.lastOrNull()?.second ?: currentServer?.url.orEmpty()
                            } else {
                                onClose()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add OPDS Server")
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Server tabs
            if (servers.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedServerIndex.coerceIn(0, servers.lastIndex),
                    edgePadding = 16.dp,
                ) {
                    servers.forEachIndexed { index, server ->
                        Tab(
                            selected = selectedServerIndex == index,
                            onClick = {
                                if (selectedServerIndex != index) {
                                    selectedServerIndex = index
                                    navHistory.clear()
                                    currentUrl = server.url
                                }
                            },
                            text = { Text(server.name) },
                        )
                    }
                }
            }

            // Body
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                if (currentServer != null) loadFeed(currentServer, currentUrl)
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                currentFeed != null -> {
                    val feed = currentFeed!!
                    if (feed.entries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No entries found in this feed")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(feed.entries, key = { it.id }) { entry ->
                                if (entry.isNavigation) {
                                    // Category / Navigation item
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                entry.navUrl?.let { nextUrl ->
                                                    navHistory.add(entry.title to nextUrl)
                                                    currentUrl = nextUrl
                                                }
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                entry.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (!entry.summary.isNullOrBlank()) {
                                                Text(
                                                    entry.summary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Outlined.NavigateNext,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    HorizontalDivider()
                                } else {
                                    // Book acquisition item
                                    OpdsBookItem(
                                        entry = entry,
                                        onDownload = { onProgress ->
                                            val result = if (currentServer != null) {
                                                onDownloadBook(currentServer, entry, onProgress)
                                            } else {
                                                Result.failure(java.io.IOException("No server selected"))
                                            }
                                            result.onSuccess {
                                                Toast.makeText(context, "Added to library: ${entry.title}", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "Download failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                            result
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOpdsServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { server ->
                onAddServer(server)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun OpdsBookItem(
    entry: OpdsEntry,
    onDownload: suspend ((Float) -> Unit) -> Result<File>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(60.dp, 88.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (entry.coverUrl != null) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.author.isNullOrBlank()) {
                Text(
                    entry.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
            if (!entry.summary.isNullOrBlank()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (downloading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.width(12.dp))

        // Action
        IconButton(
            onClick = {
                if (!downloading && !downloaded) {
                    scope.launch {
                        downloading = true
                        progress = 0f
                        val result = onDownload { progress = it }
                        downloading = false
                        if (result.isSuccess) {
                            downloaded = true
                        }
                    }
                }
            },
            enabled = !downloading && !downloaded,
        ) {
            if (downloaded) {
                Icon(Icons.Outlined.Check, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            } else if (downloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.CloudDownload, contentDescription = "Download to library")
            }
        }
    }
}

@Composable
private fun AddOpdsServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (OpdsServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add OPDS Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name (e.g. My Calibre)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("OPDS URL (http://.../opds)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        val server = OpdsServer(
                            id = "opds-" + System.currentTimeMillis(),
                            name = name.trim(),
                            url = url.trim(),
                            username = username.trim().takeIf(String::isNotBlank),
                            password = password.takeIf(String::isNotBlank),
                        )
                        onConfirm(server)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
// KMK <--
