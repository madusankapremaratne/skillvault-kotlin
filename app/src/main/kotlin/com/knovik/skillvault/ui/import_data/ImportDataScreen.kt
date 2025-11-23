package com.knovik.skillvault.ui.import_data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Jetpack Compose screen for importing CSV data.
 * Supports both URL and local file selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    viewModel: ImportDataViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onImportSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var urlInput by remember { mutableStateOf("") }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFromUri(it)
        }
    }

    // Handle success state
    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Success) {
            kotlinx.coroutines.delay(2000)
            onImportSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import CSV Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("URL") },
                    icon = { Icon(Icons.Default.Add, "URL Import") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Local File") },
                    icon = { Icon(Icons.Default.Info, "File Import") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content based on selected tab
            when (selectedTab) {
                0 -> UrlImportContent(
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    onImport = { viewModel.importFromUrl(urlInput) },
                    uiState = uiState
                )
                1 -> FileImportContent(
                    onSelectFile = { filePickerLauncher.launch("*/*") },
                    uiState = uiState
                )
            }

            // Status display
            StatusDisplay(uiState = uiState, onDismiss = { viewModel.resetState() })

            // Manual embedding generation
            EmbeddingCard(
                onGenerateEmbeddings = { viewModel.generateEmbeddings() },
                uiState = uiState
            )

            // Help text
            HelpCard()
        }
    }
}

@Composable
fun UrlImportContent(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    uiState: ImportUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import from URL",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                label = { Text("CSV File URL") },
                placeholder = { Text("https://example.com/data.csv") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = uiState !is ImportUiState.Loading
            )

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = urlInput.isNotBlank() && uiState !is ImportUiState.Loading
            ) {
                Icon(Icons.Default.Add, "Import", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from URL")
            }
        }
    }
}

@Composable
fun FileImportContent(
    onSelectFile: () -> Unit,
    uiState: ImportUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import from Local File",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Select a CSV file from your device storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ImportUiState.Loading
            ) {
                Icon(Icons.Default.Info, "Select File", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose CSV File")
            }
        }
    }
}

@Composable
fun StatusDisplay(
    uiState: ImportUiState,
    onDismiss: () -> Unit
) {
    when (uiState) {
        is ImportUiState.Loading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        is ImportUiState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Success",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Successful!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        is ImportUiState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
        ImportUiState.Idle -> {
            // No status to display
        }
    }
}

@Composable
fun EmbeddingCard(
    onGenerateEmbeddings: () -> Unit,
    uiState: ImportUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Generate Embeddings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Generate or regenerate vector embeddings for all imported resumes. " +
                      "This enables semantic search functionality.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onGenerateEmbeddings,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ImportUiState.Loading
            ) {
                Icon(Icons.Default.Refresh, "Generate", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Embeddings")
            }
        }
    }
}

@Composable
fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Supported Formats",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Kaggle Resume Dataset (9 columns)\n" +
                      "• Extended Resume Dataset (35 columns)\n" +
                      "• Custom CSV formats (auto-detected)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Tips",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Ensure CSV has headers in the first row\n" +
                      "• Maximum file size: 50MB\n" +
                      "• URL must be publicly accessible",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
