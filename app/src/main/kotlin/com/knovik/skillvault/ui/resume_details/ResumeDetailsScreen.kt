package com.knovik.skillvault.ui.resume_details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.knovik.skillvault.data.entity.Resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeDetailsScreen(
    viewModel: ResumeDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resume Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ResumeDetailsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ResumeDetailsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading resume",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(text = state.message)
                    }
                }
                is ResumeDetailsUiState.Success -> {
                    ResumeContent(
                        resume = state.resume,
                        exactMatchQuery = state.exactMatchQuery,
                        semanticMatchText = state.semanticMatchText
                    )
                }
            }
        }
    }
}

@Composable
fun ResumeContent(
    resume: Resume,
    exactMatchQuery: String? = null,
    semanticMatchText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resume.fullName.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = resume.fullName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (resume.email.isNotBlank()) {
                        Icon(
                            Icons.Default.Email, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = resume.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                if (resume.phoneNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Phone, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = resume.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Summary Section
        if (resume.summary.isNotBlank()) {
            DetailSection(title = "Summary") {
                HighlightedText(
                    text = resume.summary,
                    exactMatchQuery = exactMatchQuery,
                    semanticMatchText = semanticMatchText
                )
            }
        }

        // Skills Section
        if (resume.skills.isNotBlank()) {
            DetailSection(title = "Skills") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resume.skills.split(",").forEach { skill ->
                        if (skill.isNotBlank()) {
                            SuggestionChip(
                                onClick = { },
                                label = { 
                                    HighlightedText(
                                        text = skill.trim(),
                                        exactMatchQuery = exactMatchQuery,
                                        semanticMatchText = semanticMatchText,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Experience Section
        if (resume.experience.isNotBlank()) {
            DetailSection(title = "Experience") {
                HighlightedText(
                    text = resume.experience,
                    exactMatchQuery = exactMatchQuery,
                    semanticMatchText = semanticMatchText
                )
            }
        }

        // Education Section
        if (resume.education.isNotBlank()) {
            DetailSection(title = "Education") {
                HighlightedText(
                    text = resume.education,
                    exactMatchQuery = exactMatchQuery,
                    semanticMatchText = semanticMatchText
                )
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    exactMatchQuery: String?,
    semanticMatchText: String?,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val semanticHighlight = if (isDark) Color(0xFF004F75).copy(alpha = 0.5f) else Color(0xFFB3E5FC).copy(alpha = 0.5f)
    val exactHighlight = if (isDark) Color(0xFF665200).copy(alpha = 0.8f) else Color(0xFFFFF9C4).copy(alpha = 0.8f)

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        
        // 1. Highlight Semantic Match (Lower priority, background)
        if (!semanticMatchText.isNullOrBlank()) {
            val startIndex = text.indexOf(semanticMatchText, ignoreCase = true)
            if (startIndex >= 0) {
                val endIndex = startIndex + semanticMatchText.length
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(
                        background = semanticHighlight
                    ),
                    start = startIndex,
                    end = endIndex
                )
            }
        }

        // 2. Highlight Exact Matches (Higher priority, overlay)
        if (!exactMatchQuery.isNullOrBlank()) {
            val queryTerms = exactMatchQuery.split(" ").filter { it.isNotBlank() }
            queryTerms.forEach { term ->
                var startIndex = text.indexOf(term, ignoreCase = true)
                while (startIndex >= 0) {
                    val endIndex = startIndex + term.length
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            background = exactHighlight,
                            fontWeight = FontWeight.Bold
                        ),
                        start = startIndex,
                        end = endIndex
                    )
                    startIndex = text.indexOf(term, startIndex + 1, ignoreCase = true)
                }
            }
        }
    }

    Text(
        text = annotatedString,
        style = style,
        color = color
    )
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
