/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.QueueEditLockKey
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.MediaMetadataListItem
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.menu.QueueMenu
import com.metrolist.music.ui.menu.SelectionMediaMetadataMenu
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.playback.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import com.metrolist.music.constants.SleepTimerDefaultKey
import android.widget.Toast
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.derivedStateOf
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.MediaItem
import com.metrolist.music.constants.NavigationBarHeight
import timber.log.Timber


@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    background: Color,
    onBackgroundColor: Color,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    pureBlack: Boolean,
    showInlineLyrics: Boolean,
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    onToggleLyrics: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val sleepTimerDefaultSetTemplate = stringResource(R.string.sleep_timer_default_set)
    val bottomSheetPageState = LocalBottomSheetPageState.current

    // Listen Together state (reactive)
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }

    // Cast state - safely access castConnectionHandler to prevent crashes during service lifecycle changes
    val castHandler =
        remember(playerConnection) {
            try {
                playerConnection.service.castConnectionHandler
            } catch (e: Exception) {
                throw e
            }
        }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<String>, String>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) =
        rememberPreference(
            UseNewPlayerDesignKey,
            defaultValue = true,
        )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }

    val coroutineScope = rememberCoroutineScope()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerDefault by rememberPreference(SleepTimerDefaultKey, 30f)
    var sleepTimerValue by remember { mutableFloatStateOf(sleepTimerDefault) }
    val isAtDefault by remember {
        derivedStateOf { sleepTimerValue.roundToInt() == sleepTimerDefault.roundToInt() }
    }
    val sleepTimerStopAfterCurrentSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val sleepTimerFadeOut by rememberPreference(SleepTimerFadeOutKey, false)
    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            )
        },
        collapsedContent = {
            if (useNewPlayerDesign) {
                // New design
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 12.dp)
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                                ),
                            ),
                ) {
                    val buttonSize = 42.dp
                    val iconSize = 24.dp
                    val queueShape =
                        RoundedCornerShape(
                            topStart = 50.dp,
                            bottomStart = 50.dp,
                            topEnd = 3.dp,
                            bottomEnd = 3.dp,
                        )
                    val middleShape = RoundedCornerShape(3.dp)
                    val repeatShape =
                        RoundedCornerShape(
                            topStart = 3.dp,
                            bottomStart = 3.dp,
                            topEnd = 50.dp,
                            bottomEnd = 50.dp,
                        )

                    PlayerQueueButton(
                        icon = R.drawable.queue_music,
                        onClick = { state.expandSoft() },
                        isActive = false,
                        shape = queueShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground,
                    )

                    PlayerQueueButton(
                        icon = R.drawable.bedtime,
                        onClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        isActive = sleepTimerEnabled,
                        enabled = !isListenTogetherGuest,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        text = if (sleepTimerEnabled) makeTimeString(sleepTimerTimeLeft) else null,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground,
                    )

                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                    PlayerQueueButton(
                        icon = R.drawable.shuffle,
                        onClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        isActive = shuffleModeEnabled,
                        enabled = !isListenTogetherGuest,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground,
                    )

                    PlayerQueueButton(
                        icon = R.drawable.lyrics,
                        onClick = { onToggleLyrics() },
                        isActive = showInlineLyrics,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground,
                    )

                    PlayerQueueButton(
                        icon =
                            when (repeatMode) {
                                Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            },
                        onClick = {
                            playerConnection.player.toggleRepeatMode()
                        },
                        isActive = repeatMode != Player.REPEAT_MODE_OFF,
                        enabled = !isListenTogetherGuest,
                        shape = repeatShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier =
                            Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(textButtonColor)
                                .clickable {
                                    menuState.show {
                                        PlayerMenu(
                                            mediaMetadata = mediaMetadata,
                                            navController = navController,
                                            playerBottomSheetState = playerBottomSheetState,
                                            onShowDetailsDialog = {
                                                mediaMetadata?.id?.let {
                                                    bottomSheetPageState.show {
                                                        ShowMediaInfo(it)
                                                    }
                                                }
                                            },
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.more_vert),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = iconButtonColor,
                        )
                    }
                }
            } else {
                // Old design
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 12.dp)
                            .windowInsetsPadding(
                                WindowInsets.systemBars
                                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                            ),
                ) {
                    TextButton(
                        onClick = { state.expandSoft() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = TextBackgroundColor,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.queue),
                                color = TextBackgroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }

                    TextButton(
                        enabled = !isListenTogetherGuest,
                        onClick = {
                            if (!isListenTogetherGuest) {
                                if (sleepTimerEnabled) {
                                    playerConnection.service.sleepTimer.clear()
                                } else {
                                    showSleepTimerDialog = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.bedtime),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = TextBackgroundColor,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AnimatedContent(
                                label = "sleepTimer",
                                targetState = sleepTimerEnabled,
                            ) { enabled ->
                                if (enabled) {
                                    Text(
                                        text = makeTimeString(sleepTimerTimeLeft),
                                        color = TextBackgroundColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.basicMarquee(),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.sleep_timer),
                                        color = TextBackgroundColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.basicMarquee(),
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            onToggleLyrics()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lyrics),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = TextBackgroundColor,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.lyrics),
                                color = TextBackgroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }
                }
            }

            if (showSleepTimerDialog) {
                ActionPromptDialog(
                    titleBar = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.sleep_timer),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    },
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirm = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(
                            minute = sleepTimerValue.roundToInt(),
                            stopAfterCurrentSong = sleepTimerStopAfterCurrentSong,
                            fadeOut = sleepTimerFadeOut,
                        )
                    },
                    onCancel = {
                        showSleepTimerDialog = false
                    },
                    onReset = {
                        sleepTimerValue = sleepTimerDefault
                    },
                    content = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    pluralStringResource(
                                        R.plurals.minute,
                                        sleepTimerValue.roundToInt(),
                                        sleepTimerValue.roundToInt(),
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Spacer(Modifier.height(16.dp))

                            Slider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                                steps = (120 - 5) / 5 - 1,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isAtDefault) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                context.dataStore.edit { settings ->
                                                    settings[SleepTimerDefaultKey] = sleepTimerValue
                                                }
                                            }
                                            Toast.makeText(
                                                context,
                                                String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.set_as_default))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                context.dataStore.edit { settings ->
                                                    settings[SleepTimerDefaultKey] = sleepTimerValue
                                                }
                                            }
                                            Toast.makeText(
                                                context,
                                                String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    ) {
                                        Text(stringResource(R.string.set_as_default))
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        showSleepTimerDialog = false
                                        playerConnection.service.sleepTimer.start(
                                            minute = -1,
                                        )
                                    },
                                ) {
                                    Text(stringResource(R.string.end_of_song))
                                }
                            }
                        }
                    },
                )
            }
        },
    ) {
        val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
        val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
        val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()

        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }

        val coroutineScope = rememberCoroutineScope()

        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        val currentPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                if (currentWindowIndex in queueWindows.indices) {
                    queueWindows[currentWindowIndex].uid
                } else {
                    null
                }
            }

        var dragStartIdx by remember { mutableStateOf<Int?>(null) }

        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars
                .add(WindowInsets(top = ListItemHeight, bottom = ListItemHeight))
                .asPaddingValues(),
        ) { from, to ->
            val fromIdx = mutableQueueWindows.indexOfFirst { it.uid.hashCode() == from.key }
            val toIdx   = mutableQueueWindows.indexOfFirst { it.uid.hashCode() == to.key }
            if (fromIdx != -1 && toIdx != -1) {
                // Record the very first position before any move
                if (dragStartIdx == null) dragStartIdx = fromIdx
                mutableQueueWindows.move(fromIdx, toIdx)
                // Always keep original start, update only the destination
                dragInfo = dragStartIdx!! to toIdx
            }
        }

        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (originalFrom, finalTo) ->
                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(originalFrom, finalTo)
                    } else {
                        playerConnection.player.shuffleOrder = DefaultShuffleOrder(
                            queueWindows
                                .map { it.firstPeriodIndex }
                                .toMutableList()
                                .move(originalFrom, finalTo)
                                .toIntArray(),
                            System.currentTimeMillis(),
                        )
                    }
                    dragInfo = null
                }
                dragStartIdx = null  // reset for next drag
            }
        }

        LaunchedEffect(queueWindows) {
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(mutableQueueWindows, currentWindowIndex) {
            if (currentWindowIndex != -1) {
                lazyListState.scrollToItem(currentWindowIndex)
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(background),
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding =
                    WindowInsets.systemBars
                        .add(
                            WindowInsets(
                                top = ListItemHeight + 8.dp,
                                bottom = ListItemHeight + 8.dp,
                            ),
                        ).asPaddingValues(),
                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
            ) {
                item(key = "queue_top_spacer") {
                    Spacer(
                        modifier =
                            Modifier
                                .animateContentSize()
                                .height(8.dp),
                    )
                }

                // ── Partition queue into sections ──────────────────────────────────────
                // Items before the current index are "past" — we still show them under Up Next
                // so the user can scroll back and see what played. Items at/after current:
                //   • currentWindowIndex → Now Playing (single entry)
                //   • source == user_next || user_queue → Next in Queue (user explicitly queued)
                //   • source == null (app-loaded playlist/album/radio) → Up Next
                val windowsAfterCurrent = mutableQueueWindows
                    .mapIndexed { idx, w -> idx to w }
                    .filter { (idx, _) -> idx > currentWindowIndex }

                val userQueuedWindows = windowsAfterCurrent.filter { (_, w) ->
                    val src = w.mediaItem.mediaMetadata.extras
                        ?.getString(MusicService.QUEUE_SOURCE_KEY)
                    src == MusicService.QUEUE_SOURCE_USER_NEXT ||
                            src == MusicService.QUEUE_SOURCE_USER_QUEUE
                }
                val appQueuedWindows = windowsAfterCurrent.filter { (_, w) ->
                    w.mediaItem.mediaMetadata.extras
                        ?.getString(MusicService.QUEUE_SOURCE_KEY) == null
                }

                // Helper: builds the draggable/swipeable item content shared by all sections
                @Composable
                fun queueWindowItem(
                    index: Int,
                    window: Timeline.Window,
                    isDragging: Boolean = false,
                    reorderScope: sh.calvin.reorderable.ReorderableCollectionItemScope? = null
                ) {
                    val reorderKey = window.uid.hashCode()

                    val currentItem by rememberUpdatedState(window)
                    val isActive = window.uid == currentPlayingUid
                    val dismissBoxState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { total -> total },
                    )
                    var processedDismiss by remember { mutableStateOf(false) }
                    val removedSongMsg = stringResource(
                        R.string.removed_song_from_playlist,
                        currentItem.mediaItem.metadata?.title ?: "",
                    )
                    val undoStr = stringResource(R.string.undo)
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (!processedDismiss && !isListenTogetherGuest && (
                                    dv == SwipeToDismissBoxValue.StartToEnd ||
                                            dv == SwipeToDismissBoxValue.EndToStart
                                    )
                        ) {
                            processedDismiss = true
                            playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                            dismissJob?.cancel()
                            dismissJob = coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = removedSongMsg,
                                    actionLabel = undoStr,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    playerConnection.player.addMediaItem(currentItem.mediaItem)
                                    playerConnection.player.moveMediaItem(
                                        mutableQueueWindows.size,
                                        currentItem.firstPeriodIndex,
                                    )
                                }
                            }
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) processedDismiss = false
                    }

                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) selection.add(window.mediaItem.mediaId)
                        else selection.remove(window.mediaItem.mediaId)
                    }

                    val content: @Composable () -> Unit = {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            MediaMetadataListItem(
                                mediaMetadata = window.mediaItem.metadata!!,
                                isSelected = false,
                                isActive = isActive,
                                isPlaying = isPlaying && isActive,
                                isInQueue = true,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = window.mediaItem.mediaId in selection,
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        if (!isListenTogetherGuest) {
                                            IconButton(
                                                onClick = {
                                                    menuState.show {
                                                        QueueMenu(
                                                            mediaMetadata = window.mediaItem.metadata!!,
                                                            navController = navController,
                                                            playerBottomSheetState = playerBottomSheetState,
                                                            onShowDetailsDialog = {
                                                                window.mediaItem.mediaId.let {
                                                                    bottomSheetPageState.show {
                                                                        ShowMediaInfo(it)
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.more_vert),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                        if (!locked && !isListenTogetherGuest && reorderScope != null) {
                                            IconButton(
                                                onClick = {
                                                },
                                                modifier = with(reorderScope){ Modifier.draggableHandle()}
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(window.mediaItem.mediaId !in selection)
                                            } else if (!isListenTogetherGuest) {
                                                if (index == currentWindowIndex) {
                                                    if (isCasting) {
                                                        if (castIsPlaying) castHandler?.pause()
                                                        else castHandler?.play()
                                                    } else {
                                                        playerConnection.togglePlayPause()
                                                    }
                                                } else {
                                                    if (isCasting) {
                                                        val navigated = castHandler
                                                            ?.navigateToMediaIfInQueue(
                                                                window.mediaItem.mediaId
                                                            ) ?: false
                                                        if (!navigated)
                                                            playerConnection.player.seekToDefaultPosition(
                                                                window.firstPeriodIndex
                                                            )
                                                    } else {
                                                        playerConnection.player.seekToDefaultPosition(
                                                            window.firstPeriodIndex
                                                        )
                                                        playerConnection.player.playWhenReady =
                                                            true
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                inSelectMode = true
                                                onCheckedChange(true)
                                            }
                                        },
                                    ),
                            )
                        }
                    }

                    if (locked) {
                        content()
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {},
                        ) { content() }
                    }
                }

                // ── NOW PLAYING ────────────────────────────────────────────────────────
                item(key = "section_now_playing") {
                    QueueSectionHeader(
                        title = stringResource(R.string.queue_now_playing),
                        modifier = Modifier.animateItem(),
                    )
                }
                val nowPlayingWindow = mutableQueueWindows.getOrNull(currentWindowIndex)
                if (nowPlayingWindow != null) {
                    item(key = nowPlayingWindow.uid.hashCode()) {
                        // No animateItem() here — it fights animateContentSize on the
                        // now-playing card and causes the lag + scroll jump on song switch.
                        ReorderableItem(state = reorderableState, key = nowPlayingWindow.uid.hashCode()) { isDragging ->
                            queueWindowItem(currentWindowIndex, nowPlayingWindow, isDragging, this)
                        }
                    }
                }

                // ── NEXT IN QUEUE (user-queued) ────────────────────────────────────────
                if (userQueuedWindows.isNotEmpty()) {
                    item(key = "section_next_in_queue") {
                        QueueSectionHeader(
                            title = stringResource(R.string.queue_next_in_queue),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    itemsIndexed(
                        items = userQueuedWindows,
                        key = { _, (_, w) -> w.uid.hashCode() },
                    ) { _, (realIdx, window) ->
                        ReorderableItem(state = reorderableState, key = window.uid.hashCode()) { isDragging ->
                            queueWindowItem(realIdx, window, isDragging, this)
                        }
                    }
                }

                // ── UP NEXT (app-queued — playlist / album / radio) ────────────────────
                if (appQueuedWindows.isNotEmpty()) {
                    item(key = "section_up_next") {
                        QueueSectionHeader(
                            title = stringResource(R.string.queue_up_next),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    itemsIndexed(
                        items = appQueuedWindows,
                        key = { _, (_, w) -> w.uid.hashCode() },
                    ) { _, (realIdx, window) ->
                        ReorderableItem(state = reorderableState, key = window.uid.hashCode()) { isDragging ->
                            queueWindowItem(realIdx, window, isDragging, this)
                        }
                    }
                }

                // ── SUGGESTIONS (automix) ──────────────────────────────────────────────
                if (automix.isNotEmpty()) {
                    item(key = "section_suggestions") {
                        QueueSectionHeader(
                            title = stringResource(R.string.queue_suggestions),
                            modifier = Modifier.animateItem(),
                        )
                    }

                    itemsIndexed(
                        items = automix,
                        key = { _, it -> it.mediaId },
                    ) { automixIndex, item ->
                        val metadata = item.metadata ?: return@itemsIndexed

                        Row(horizontalArrangement = Arrangement.Center) {
                            MediaMetadataListItem(
                                mediaMetadata = metadata,
                                trailingContent = {
                                    if (!isListenTogetherGuest) {
                                        IconButton(
                                            onClick = {
                                                playerConnection.service.playNextAutomix(item, automixIndex)
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.playlist_play),
                                                contentDescription = null,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                playerConnection.service.addToQueueAutomix(item, automixIndex)
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.queue_music),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            menuState.show {
                                                QueueMenu(
                                                    mediaMetadata = metadata,   // ← consistent
                                                    navController = navController,
                                                    playerBottomSheetState = playerBottomSheetState,
                                                    onShowDetailsDialog = {
                                                        item.mediaId.let {
                                                            bottomSheetPageState.show {
                                                                ShowMediaInfo(it)
                                                            }
                                                        }
                                                    },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                } else {
                    Timber.tag("QueueScreen")
                        .d("No automix suggestions available, skipping section")
                }
                item(key = "queue_bottom_spacer") {
                    Spacer(
                        modifier =
                            Modifier
                                .animateContentSize()
                                .height(WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 22.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { }
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHighest)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 12.dp, vertical = 18.dp), // Outer margins around the header row
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Color.Transparent)
            ) {
                // --- LEFT BUTTON (Close button appears only in selection mode) ---
                AnimatedVisibility(
                    visible = inSelectMode,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        IconButton(onClick = onExitSelectionMode) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // --- MAIN MIDDLE PILL (Holds the dynamic titles and labels) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = queueTitle.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryFixedVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Subtitle changes content structurally depending on inSelectMode
                        Text(
                            text = if (inSelectMode) {
                                pluralStringResource(R.plurals.n_selected, selection.size, selection.size)
                            } else {
                                pluralStringResource(R.plurals.n_song, queueWindows.size, queueWindows.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryFixed
                        )
                    }
                }

                // --- RIGHT ACTION BUTTON PILL ---
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    // Conditionally show either Lock or Checkbox based on selection mode
                    if (!inSelectMode) {
                        IconButton(onClick = { locked = !locked }) {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        val count = selection.size
                        Checkbox(
                            checked = count == mutableQueueWindows.size && count > 0,
                            onCheckedChange = {
                                if (count == mutableQueueWindows.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    mutableQueueWindows.forEach { selection.add(it.mediaItem.mediaId) }
                                }
                            }
                        )
                    }
                }
            }

            if (pureBlack) {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            }
        }
        val shuffleInteractionSource = remember { MutableInteractionSource() }
        val repeatInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHighest)
                .fillMaxWidth()
                .height(NavigationBarHeight * 1.4f + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
                .align(Alignment.BottomCenter)
                .clickable { state.collapseSoft() }
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(12.dp),
        ) {
            // --- LEFT BUTTON (SHUFFLE OR MORE OPTIONS) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(NavigationBarHeight * 1.1f)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                AnimatedContent(
                    targetState = inSelectMode,
                    label = "LeftButtonTransition"
                ) { selectModeActive ->
                    if (!selectModeActive) {
                        // Default State: Shuffle Button
                        IconButton(
                            enabled = !isListenTogetherGuest,
                            interactionSource = shuffleInteractionSource,
                            onClick = {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(
                                        if (playerConnection.player.shuffleModeEnabled) playerConnection.player.currentMediaItemIndex else 0,
                                    )
                                }.invokeOnCompletion {
                                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = "Shuffle",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    } else {
                        // Selection Mode State: More Options Button
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SelectionMediaMetadataMenu(
                                        songSelection = selectedSongs,
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode,
                                        currentItems = selectedItems,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert), // 3 vertical dots icon
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // --- MIDDLE BUTTON (EXPAND MORE) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.4f)
                    .height(NavigationBarHeight * 1.1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp, 24.dp)
                )
            }

            // --- RIGHT BUTTON (REPEAT OR REMOVE FROM QUEUE) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(NavigationBarHeight * 1.1f)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                AnimatedContent(
                    targetState = inSelectMode,
                    label = "RightButtonTransition"
                ) { selectModeActive ->
                    if (!selectModeActive) {
                        // Default State: Repeat Button
                        IconButton(
                            enabled = !isListenTogetherGuest,
                            interactionSource = repeatInteractionSource,
                            onClick = playerConnection.player::toggleRepeatMode,
                        ) {
                            Icon(
                                painter = painterResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                        else -> throw IllegalStateException()
                                    },
                                ),
                                contentDescription = "Repeat",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    } else {
                        // Selection Mode State: Remove From Queue Button
                        IconButton(
                            enabled = selection.isNotEmpty(), // Disable if no items are highlighted
                            onClick = {
                                val indicesToRemove = selection.mapNotNull { mediaId ->
                                    val index = mutableQueueWindows.indexOfFirst { it.mediaItem.mediaId == mediaId }
                                    if (index != -1) index else null
                                }
                                val sortedDescendingIndices = indicesToRemove.sortedDescending()
                                sortedDescendingIndices.forEach { indexInQueue ->
                                    playerConnection.player.removeMediaItem(indexInQueue)
                                }
                                onExitSelectionMode()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete), // Your monitor/minus icon
                                contentDescription = "Remove selected from queue",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(
                    bottom = ListItemHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                )
                .height(ListItemHeight * 2)
                .align(Alignment.BottomCenter),
        )
    }
}

/**
 * Spotify-style section header for queue sections (Now Playing, Next in Queue, Up Next, Suggestions).
 */
@Composable
private fun QueueSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 24.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun PlayerQueueButton(
    modifier: Modifier = Modifier,
    icon: Int,
    onClick: () -> Unit,
    isActive: Boolean,
    enabled: Boolean = true,
    shape: RoundedCornerShape,
    text: String? = null,
    textButtonColor: Color,
    iconButtonColor: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    textBackgroundColor: Color,
    playerBackground: PlayerBackgroundStyle,
) {
    val buttonModifier =
        Modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)

    val alphaFactor = if (enabled) 1f else 0.35f

    val appliedModifier =
        if (isActive) {
            modifier
                .then(buttonModifier.background(textButtonColor))
                .alpha(alphaFactor)
        } else {
            modifier
                .then(
                    buttonModifier.border(
                        width = 1.dp,
                        color = textButtonColor.copy(alpha = 0.3f),
                        shape = shape,
                    ),
                )
                .alpha(alphaFactor)
        }

    Box(
        modifier = appliedModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                color = iconButtonColor.copy(alpha = if (enabled) 1f else 0.6f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
            )
        } else {
            val baseTint =
                if (isActive) {
                    iconButtonColor
                } else {
                    when (playerBackground) {
                        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                            Color.White
                        }

                        PlayerBackgroundStyle.DEFAULT -> {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    }
                }
            val finalTint = if (enabled) baseTint else baseTint.copy(alpha = 0.5f)
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = finalTint,
            )
        }
    }
}