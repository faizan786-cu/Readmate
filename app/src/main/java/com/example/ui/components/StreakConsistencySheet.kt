package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.local.database.entity.RetentionStreakStatus
import com.example.ui.util.StreakEngine
import com.example.ui.util.StreakInfo
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Strict Monochrome Obsidian Palette
private val DeepObsidian = Color(0xFF0B0B0E)
private val ZincSurface = Color(0xFF141418)
private val SlateBorder = Color(0xFF27272F)
private val SlateBadgeBg = Color(0xFF27272F)
private val SlateBadgeBorder = Color(0xFF3F3F46)
private val CrispWhite = Color(0xFFFFFFFF)
private val TextSilver = Color(0xFFA1A1AA)
private val ZincMuted = Color(0xFF71717A)
private val TextRestDay = Color(0xFF52525B)
private val TextFutureDay = Color(0xFF3F3F46)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakConsistencySheet(
    streakInfo: StreakInfo,
    readingTimestamps: List<Long>,
    dailyRetentionState: DailyRetentionStateEntity?,
    allRetentionStates: List<DailyRetentionStateEntity> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepObsidian,
        contentColor = CrispWhite,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null,
        modifier = modifier.testTag("streak_sheet")
    ) {
        StreakConsistencyContent(
            streakInfo = streakInfo,
            readingTimestamps = readingTimestamps,
            dailyRetentionState = dailyRetentionState,
            allRetentionStates = allRetentionStates,
            onClose = onDismiss
        )
    }
}

@Composable
fun StreakConsistencyContent(
    streakInfo: StreakInfo,
    readingTimestamps: List<Long>,
    dailyRetentionState: DailyRetentionStateEntity?,
    allRetentionStates: List<DailyRetentionStateEntity> = emptyList(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }

    // Aggregate timestamps into dates with count of snippets/sessions
    val daySnipCounts = remember(readingTimestamps) {
        val map = mutableMapOf<LocalDate, Int>()
        for (ts in readingTimestamps) {
            val date = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
            map[date] = (map[date] ?: 0) + 1
        }
        map
    }

    // Map of frozen/debt dates
    val frozenDates = remember(allRetentionStates) {
        allRetentionStates
            .filter { it.streakStatus == RetentionStreakStatus.FROZEN_DEBT }
            .mapNotNull {
                try {
                    LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (_: Exception) {
                    null
                }
            }
            .toSet()
    }

    // Longest streak calculation
    val longestStreakDays = remember(daySnipCounts, streakInfo.activeStreakCount) {
        val validDays = daySnipCounts.filter { it.value >= StreakEngine.REQUIRED_SNIPS_PER_DAY }.keys.sorted()
        var maxStreak = 0
        var current = 0
        var prevDate: LocalDate? = null
        for (d in validDays) {
            if (prevDate == null || d == prevDate.plusDays(1)) {
                current++
            } else {
                current = 1
            }
            if (current > maxStreak) maxStreak = current
            prevDate = d
        }
        maxOf(maxStreak, streakInfo.activeStreakCount)
    }

    // Total reading sessions count
    val totalSessionsCount = remember(readingTimestamps, daySnipCounts) {
        if (readingTimestamps.isNotEmpty()) readingTimestamps.size else daySnipCounts.values.sum()
    }

    // Monthly consistency rate for displayed month
    val monthlyConsistencyRate = remember(displayedMonth, daySnipCounts, today) {
        val daysToConsider = if (displayedMonth == YearMonth.from(today)) {
            today.dayOfMonth
        } else {
            displayedMonth.lengthOfMonth()
        }
        if (daysToConsider <= 0) return@remember 0
        val activeCount = (1..daysToConsider).count { dayNumber ->
            val d = displayedMonth.atDay(dayNumber)
            (daySnipCounts[d] ?: 0) >= StreakEngine.REQUIRED_SNIPS_PER_DAY
        }
        ((activeCount.toFloat() / daysToConsider) * 100).toInt()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 36.dp)
    ) {
        // Top Header Row with Title and Close Button (Directive 5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONSISTENCY CENTER",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp
                ),
                color = TextSilver
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("close_streak_sheet_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close consistency center",
                    tint = CrispWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Streak Banner (Directive 2)
        HeroStreakBanner(
            activeStreakCount = streakInfo.activeStreakCount,
            dailyRetentionState = dailyRetentionState
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Streak Calendar (Directive 3)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ZincSurface),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("streak_calendar_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Calendar Header Row: Month Year + Prev/Next Controls
                CalendarHeaderRow(
                    displayedMonth = displayedMonth,
                    onPrevMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                    onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Day Labels Row (Su, Mo, Tu, We, Th, Fr, Sa)
                WeekdayLabelsRow()

                Spacer(modifier = Modifier.height(10.dp))

                // 7-column Date Grid
                MonthlyDateGrid(
                    displayedMonth = displayedMonth,
                    today = today,
                    daySnipCounts = daySnipCounts,
                    frozenDates = frozenDates
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Performance Stats Micro-Row (Directive 4)
                PerformanceStatsMicroRow(
                    longestStreak = longestStreakDays,
                    totalSessions = totalSessionsCount,
                    consistencyRate = monthlyConsistencyRate
                )
            }
        }
    }
}

/**
 * Hero Streak Banner in Obsidian aesthetic pedestal container.
 */
@Composable
private fun HeroStreakBanner(
    activeStreakCount: Int,
    dailyRetentionState: DailyRetentionStateEntity?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ZincSurface),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_streak_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Content Area
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$activeStreakCount",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            letterSpacing = (-1.5).sp,
                            lineHeight = 52.sp
                        ),
                        color = CrispWhite,
                        modifier = Modifier.testTag("hero_streak_counter")
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "DAY STREAK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.4.sp
                        ),
                        color = TextSilver
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val motivationalQuote = when {
                        activeStreakCount >= 30 -> "Mastery is forged in unbroken consistency."
                        activeStreakCount >= 14 -> "Discipline compounds into effortless habit."
                        activeStreakCount >= 7 -> "One full week of relentless focus."
                        activeStreakCount >= 3 -> "Momentum is alive. Keep building daily."
                        activeStreakCount >= 1 -> "The flame is lit. Return tomorrow to sustain it."
                        else -> "Consistency compounds into true mastery."
                    }

                    Text(
                        text = motivationalQuote,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        color = TextSilver
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Anchor: Large, refined geometric flame glyph in executive badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SlateBadgeBg)
                        .border(1.dp, SlateBadgeBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Active streak flame",
                        tint = CrispWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Streak Freeze / Status Sub-Pod
            val freezeLabel = when {
                dailyRetentionState?.streakStatus == RetentionStreakStatus.FROZEN_DEBT -> "Streak Freeze: Inactive (Debt Frozen)"
                dailyRetentionState?.isFirstDayGracePeriod == true -> "Streak Shield: Grace Period Active"
                else -> "Streak Freeze: 1 Available"
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DeepObsidian,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_freeze_status")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = TextSilver,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = freezeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = TextSilver
                    )
                }
            }
        }
    }
}

/**
 * Month & Year header with arrow navigation.
 */
@Composable
private fun CalendarHeaderRow(
    displayedMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthTitle = remember(displayedMonth) {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        displayedMonth.format(formatter)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevMonth,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .testTag("prev_month_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = CrispWhite,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = monthTitle,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            ),
            color = CrispWhite
        )

        IconButton(
            onClick = onNextMonth,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .testTag("next_month_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = CrispWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Weekday labels (Su, Mo, Tu, We, Th, Fr, Sa) in muted slate.
 */
@Composable
private fun WeekdayLabelsRow() {
    val weekdays = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdays.forEach { dayLabel ->
            Text(
                text = dayLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = ZincMuted
            )
        }
    }
}

/**
 * 7-column responsive monthly grid mapping days of the selected month.
 */
@Composable
private fun MonthlyDateGrid(
    displayedMonth: YearMonth,
    today: LocalDate,
    daySnipCounts: Map<LocalDate, Int>,
    frozenDates: Set<LocalDate>
) {
    val firstDayOfMonth = remember(displayedMonth) { displayedMonth.atDay(1) }
    val daysInMonth = remember(displayedMonth) { displayedMonth.lengthOfMonth() }
    val startDayOffset = remember(firstDayOfMonth) { firstDayOfMonth.dayOfWeek.value % 7 }
    val totalSlots = remember(startDayOffset, daysInMonth) {
        val total = startDayOffset + daysInMonth
        if (total % 7 == 0) total else ((total / 7) + 1) * 7
    }

    val rows = remember(totalSlots) { totalSlots / 7 }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in 0 until 7) {
                    val slotIndex = (r * 7) + col
                    if (slotIndex < startDayOffset || slotIndex >= startDayOffset + daysInMonth) {
                        // Blank slot outside month bounds
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val dayNumber = slotIndex - startDayOffset + 1
                        val date = displayedMonth.atDay(dayNumber)
                        val isToday = date == today
                        val isFuture = date.isAfter(today)
                        val snipCount = daySnipCounts[date] ?: 0
                        val isActiveReadingDay = snipCount >= StreakEngine.REQUIRED_SNIPS_PER_DAY
                        val isFrozenDay = frozenDates.contains(date)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CalendarDateCell(
                                dayNumber = dayNumber,
                                isToday = isToday,
                                isFuture = isFuture,
                                isActiveReadingDay = isActiveReadingDay,
                                isFrozenDay = isFrozenDay
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Calendar Date Cell based on the Obsidian design specifications:
 * - Active Reading Days (Streak Maintained): Circular high-contrast pill highlight (#FFFFFF solid circle with #0B0B0E bold inverted text).
 * - Today's Date: Outlined circle with a crisp 1.5dp border (#FFFFFF text and border).
 * - Frozen Days: Subtle outlined shield badge (#71717A).
 * - Inactive / Past Rest Days: Clean numerical text (#52525B).
 * - Future Days: Dimmed numerical text (#3F3F46).
 */
@Composable
private fun CalendarDateCell(
    dayNumber: Int,
    isToday: Boolean,
    isFuture: Boolean,
    isActiveReadingDay: Boolean,
    isFrozenDay: Boolean
) {
    when {
        isActiveReadingDay -> {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CrispWhite),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = DeepObsidian
                )
            }
        }
        isToday -> {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, CrispWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = CrispWhite
                )
            }
        }
        isFrozenDay -> {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, ZincMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = ZincMuted
                )
            }
        }
        isFuture -> {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = TextFutureDay,
                textAlign = TextAlign.Center
            )
        }
        else -> {
            // Past rest day
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = TextRestDay,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Performance Stats Micro-Row:
 * - "Longest Streak: X Days"
 * - "Total Reading Sessions: Y"
 * - "Consistency Rate: Z%"
 */
@Composable
private fun PerformanceStatsMicroRow(
    longestStreak: Int,
    totalSessions: Int,
    consistencyRate: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DeepObsidian,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("performance_stats_pod")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "LONGEST STREAK",
                value = "$longestStreak ${if (longestStreak == 1) "Day" else "Days"}",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(1.dp)
                    .background(SlateBorder)
            )

            StatItem(
                label = "TOTAL SESSIONS",
                value = "$totalSessions",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(1.dp)
                    .background(SlateBorder)
            )

            StatItem(
                label = "CONSISTENCY",
                value = "$consistencyRate%",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.2.sp
            ),
            color = CrispWhite
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                letterSpacing = 0.8.sp
            ),
            color = TextSilver,
            textAlign = TextAlign.Center
        )
    }
}
