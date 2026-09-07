package com.example.ui.screens.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReadMateApplication
import com.example.ui.components.ApiKeySetupDialog
import com.example.ui.viewmodel.AddBookViewModel
import com.example.ui.viewmodel.AppViewModelProvider
import java.util.Locale

// Obsidian Monochrome Theme
private val SheetBackground = Color(0xFF0B0B0E)
private val CardSurface = Color(0xFF141418)
private val TrackSurface = Color(0xFF18181B)
private val FineBorder = Color(0xFF27272A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextOffWhite = Color(0xFFFAFAFA)
private val TextMutedSilver = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val ErrorBackground = Color(0xFF271418)
private val ErrorText = Color(0xFFF87171)

/**
 * Custom dashed border modifier for tactile drop zone aesthetic.
 */
private fun Modifier.dashedBorder(
    strokeWidth: Dp = 1.dp,
    color: Color = FineBorder,
    cornerRadius: Dp = 16.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidthPx = strokeWidth.toPx()
    val cornerRadiusPx = cornerRadius.toPx()
    val dashLengthPx = dashLength.toPx()
    val gapLengthPx = gapLength.toPx()

    val pathEffect = PathEffect.dashPathEffect(
        floatArrayOf(dashLengthPx, gapLengthPx),
        0f
    )

    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = pathEffect
        )
    )
}

/**
 * Streamlined 1-tap PDF upload bottom sheet modal for zero-friction book ingestion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookBottomSheet(
    onDismissRequest: () -> Unit,
    onBookCreated: (Long) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    viewModel: AddBookViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val app = context.applicationContext as ReadMateApplication
    var showApiKeySetupDialog by remember { mutableStateOf(false) }

    if (showApiKeySetupDialog) {
        ApiKeySetupDialog(
            isOpen = true,
            onDismiss = { showApiKeySetupDialog = false },
            onKeySaved = {
                showApiKeySetupDialog = false
            },
            secureStorage = app.secureApiKeyStorage,
            userPreferencesRepository = app.userPreferencesRepository
        )
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onPdfSelected(context, uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!viewModel.isCreatingBook) {
                onDismissRequest()
            }
        },
        sheetState = sheetState,
        containerColor = SheetBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color(0xFF27272F), CircleShape)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp, top = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Book",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Upload PDF to auto-detect title, author & chapters",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = TextZincMuted
                        )
                    )
                }

                if (!viewModel.isCreatingBook) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_add_book_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMutedSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Content: Loading State vs Ingestion Controls
            if (viewModel.isCreatingBook) {
                // Minimalist Non-Intrusive Loader View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(16.dp))
                        .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                        .testTag("add_book_loading_container"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .testTag("add_book_linear_progress"),
                        color = TextWhite,
                        trackColor = TrackSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Extracting title, author & chapter structure...",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = TextMutedSilver,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Gemini Flash-Lite parsing document layout",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = TextZincMuted
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Ingestion Controls
                if (viewModel.isImportingPdf) {
                    // Importing State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(CardSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, FineBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Reading PDF Document...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextWhite
                                )
                            )
                        }
                    }
                } else if (!viewModel.selectedFilePath.isNullOrBlank()) {
                    // File Attached State
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("add_book_file_attached_tile"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TrackSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FineBorder),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = "Attached PDF",
                                    tint = TextMutedSilver,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.selectedFileName ?: "Document.pdf",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextOffWhite
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sizeText = viewModel.selectedFileSizeMb?.let {
                                String.format(Locale.US, "%.1f MB", it)
                            } ?: "PDF File"
                            Text(
                                text = "$sizeText · ${viewModel.selectedTotalPages} pages",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = TextZincMuted
                                )
                            )
                        }

                        IconButton(
                            onClick = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("replace_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Replace PDF",
                                tint = TextMutedSilver,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = viewModel::removePdf,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("remove_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove PDF",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    // Default State (No File Selected) - Tactile Drop Zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(CardSurface, RoundedCornerShape(16.dp))
                            .dashedBorder(
                                strokeWidth = 1.dp,
                                color = FineBorder,
                                cornerRadius = 16.dp
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!app.secureApiKeyStorage.hasApiKey()) {
                                    showApiKeySetupDialog = true
                                } else {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            .testTag("attach_pdf_drop_zone"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = "Upload PDF",
                                tint = TextWhite,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Attach PDF Document",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Title, author, and all chapters will be auto-detected",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = TextZincMuted
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Error Message if any
                if (!viewModel.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ErrorBackground, RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF451A1A), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.errorMessage ?: "",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ErrorText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::clearError,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = ErrorText,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary CTA: "Create Book →"
                Button(
                    onClick = {
                        viewModel.createBook { newBookId ->
                            onDismissRequest()
                            onBookCreated(newBookId)
                        }
                    },
                    enabled = !viewModel.selectedFilePath.isNullOrBlank() && !viewModel.isImportingPdf,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextWhite,
                        contentColor = Color(0xFF0B0B0E),
                        disabledContainerColor = Color(0xFF27272A),
                        disabledContentColor = Color(0xFF71717A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_book_button")
                ) {
                    Text(
                        text = "Create Book →",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    )
                }
            }
        }
    }
}
