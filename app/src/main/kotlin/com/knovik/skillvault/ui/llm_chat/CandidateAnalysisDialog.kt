package com.knovik.skillvault.ui.llm_chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateAnalysisDialog(
    resumeId: Long,
    onDismiss: () -> Unit,
    viewModel: LlmChatViewModel = hiltViewModel()
) {
    var roleDescription by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkModelAvailability()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Candidate Analysis") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val state = uiState) {
                    is ChatUiState.ModelMissing -> {
                        Text(
                            "Gemma 2 Model not found on device.\nPlease download 'gemma-2b-it-cpu-int4.bin' and place it in app storage.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is ChatUiState.Idle -> {
                        val maxChar = 300
                        Column {
                            OutlinedTextField(
                                value = roleDescription,
                                onValueChange = { 
                                    if (it.length <= maxChar) roleDescription = it 
                                },
                                label = { Text("Enter Job Role / Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                supportingText = {
                                    Text(
                                        text = "${roleDescription.length} / $maxChar",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            )
                        }
                    }
                    is ChatUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        Text("Analyzing candidate fit... This may take a moment.", style = MaterialTheme.typography.bodySmall)
                    }
                    is ChatUiState.Success -> {
                        Text("Analysis Result:", style = MaterialTheme.typography.titleSmall)
                        Text(state.response)
                    }
                    is ChatUiState.Error -> {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (uiState is ChatUiState.Idle) {
                Button(
                    onClick = { viewModel.analyzeCandidateFit(resumeId, roleDescription) },
                    enabled = roleDescription.isNotBlank()
                ) {
                    Text("Analyze Fit")
                }
            } else if (uiState is ChatUiState.Success || uiState is ChatUiState.Error) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (uiState !is ChatUiState.Loading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
