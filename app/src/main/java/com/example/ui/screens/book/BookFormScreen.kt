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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.AddBookViewModel
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.BookFormViewModel
import java.util.Locale

// Monochrome Palette
private val CanvasBackground = Color(0xFF0B0B0E)
private val CardSurface = Color(0xFF141418)
private val TrackSurface = Color(0xFF18181B)
private val FineBorder = Color(0xFF27272A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextOffWhite = Color(0xFFFAFAFA)
private val TextMutedSilver = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val ErrorBackground = Color(0xFF271418)
private val ErrorText = Color(0xFFF87171)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    onNavigateBack: () -> Unit,
    onBookSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bookFormViewModel: BookFormViewModel = viewModel(factory = AppViewModelProvider.Factory),
    addBookViewModel: AddBookViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val isEditMode = bookFormViewModel.bookId != null && bookFormViewModel.bookId > 0
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (isEditMode) {
                bookFormViewModel.onPdfSelected(context, uri)
            } else {
                addBookViewModel.onPdfSelected(context, uri)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground),
        containerColor = CanvasBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Book" else "Add Book",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("book_form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasBackground,
                    titleContentColor = TextWhite,
                    navigationIconContentColor = TextWhite
                )
            )
        }
    ) { innerPadding ->
        if (isEditMode) {
            // Edit Mode: Update title, author, description, PDF
            if (bookFormViewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TextWhite)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "Update book details and attached document",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextZincMuted
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Title Field
                        OutlinedTextField(
                            value = bookFormViewModel.title,
                            onValueChange = bookFormViewModel::onTitleChange,
                            label = { Text("Book Title *", color = TextZincMuted) },
                            placeholder = { Text("e.g. The Psychology of Money", color = TextZincMuted) },
                            isError = bookFormViewModel.titleError != null,
                            supportingText = {
                                if (bookFormViewModel.titleError != null) {
                                    Text(
                                        text = bookFormViewModel.titleError ?: "",
                                        color = ErrorText
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_title_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextOffWhite,
                                focusedBorderColor = TextWhite,
                                unfocusedBorderColor = FineBorder,
                                focusedContainerColor = CardSurface,
                                unfocusedContainerColor = CardSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Author Field
                        OutlinedTextField(
                            value = bookFormViewModel.author,
                            onValueChange = bookFormViewModel::onAuthorChange,
                            label = { Text("Author (Optional)", color = TextZincMuted) },
                            placeholder = { Text("e.g. Morgan Housel", color = TextZincMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_author_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextOffWhite,
                                focusedBorderColor = TextWhite,
                                unfocusedBorderColor = FineBorder,
                                focusedContainerColor = CardSurface,
                                unfocusedContainerColor = CardSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // PDF Attachment Section
                        Text(
                            text = "PDF Document",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Attached document for reader, flip pages, and snip tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextZincMuted
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (bookFormViewModel.isImportingPdf) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSurface, RoundedCornerShape(16.dp))
                                    .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Importing PDF document...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else if (!bookFormViewModel.pdfFilePath.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSurface, RoundedCornerShape(16.dp))
                                    .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = TrackSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FineBorder),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.PictureAsPdf,
                                            contentDescription = null,
                                            tint = TextMutedSilver,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookFormViewModel.pdfFileName ?: "Attached Document.pdf",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = TextOffWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${bookFormViewModel.pdfTotalPages} pages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextZincMuted
                                    )
                                }

                                IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                                    Icon(Icons.Outlined.Edit, "Replace", tint = TextMutedSilver, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { bookFormViewModel.removePdf(context) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(CardSurface, RoundedCornerShape(16.dp))
                                    .dashedBorder(1.dp, FineBorder, 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.UploadFile, null, tint = TextWhite, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Attach PDF Document", fontWeight = FontWeight.SemiBold, color = TextWhite, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description Field
                        OutlinedTextField(
                            value = bookFormViewModel.description,
                            onValueChange = bookFormViewModel::onDescriptionChange,
                            label = { Text("Description (Optional)", color = TextZincMuted) },
                            placeholder = { Text("Brief notes about this book...", color = TextZincMuted) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_description_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextOffWhite,
                                focusedBorderColor = TextWhite,
                                unfocusedBorderColor = FineBorder,
                                focusedContainerColor = CardSurface,
                                unfocusedContainerColor = CardSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                bookFormViewModel.saveBook(onBookSaved)
                            },
                            enabled = !bookFormViewModel.isSubmitting && !bookFormViewModel.isImportingPdf,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = Color(0xFF0B0B0E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_book_button")
                        ) {
                            if (bookFormViewModel.isSubmitting) {
                                CircularProgressIndicator(color = Color(0xFF0B0B0E), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Create Mode: Zero-Friction 1-Tap PDF Ingestion Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "1-Tap PDF Book Ingestion",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Attach any PDF document to automatically detect book title, author, and all chapter boundaries with Flash-Lite AI.",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = TextZincMuted
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (addBookViewModel.isCreatingBook) {
                        // Minimalist Non-Intrusive Loading Container
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 24.dp, vertical = 36.dp)
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

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Extracting title, author & chapter structure...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
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
                        if (addBookViewModel.isImportingPdf) {
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
                                    Spacer(modifier = Modifier.height(12.dp))
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
                        } else if (!addBookViewModel.selectedFilePath.isNullOrBlank()) {
                            // File Attached State
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSurface, RoundedCornerShape(16.dp))
                                    .border(1.dp, FineBorder, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 18.dp, vertical = 16.dp)
                                    .testTag("add_book_file_attached_tile"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = TrackSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FineBorder),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.PictureAsPdf,
                                            contentDescription = "Attached PDF",
                                            tint = TextMutedSilver,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = addBookViewModel.selectedFileName ?: "Document.pdf",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextOffWhite
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val sizeText = addBookViewModel.selectedFileSizeMb?.let {
                                        String.format(Locale.US, "%.1f MB", it)
                                    } ?: "PDF File"
                                    Text(
                                        text = "$sizeText · ${addBookViewModel.selectedTotalPages} pages",
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
                                        .size(36.dp)
                                        .testTag("replace_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Replace PDF",
                                        tint = TextMutedSilver,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = addBookViewModel::removePdf,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("remove_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove PDF",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
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
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
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
                        if (!addBookViewModel.errorMessage.isNullOrBlank()) {
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
                                    text = addBookViewModel.errorMessage ?: "",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ErrorText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = addBookViewModel::clearError,
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

                        Spacer(modifier = Modifier.height(28.dp))

                        // Primary CTA: "Create Book →"
                        Button(
                            onClick = {
                                addBookViewModel.createBook { newBookId ->
                                    onBookSaved(newBookId)
                                }
                            },
                            enabled = !addBookViewModel.selectedFilePath.isNullOrBlank() && !addBookViewModel.isImportingPdf,
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
    }
}
