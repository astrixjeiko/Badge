package com.astrix.badge

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Drag-to-reorder for rows inside a LazyColumn. Rows are identified by their keys.
class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val sameGroup: (Any, Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Boolean,   // returns true if the order changed
    private val onDrop: () -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)
    private var waitingForLayoutFrom: Int? = null

    private val draggingInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    // How far the dragged row is drawn from where its slot currently is
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    fun start(key: Any) {
        if (draggingKey != null) return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = info.offset
        draggedDelta = 0f
        waitingForLayoutFrom = null
    }

    fun drag(dy: Float) {
        draggedDelta += dy
        val dragging = draggingInfo ?: return

        val group = listState.layoutInfo.visibleItemsInfo.filter { sameGroup(dragging.key, it.key) }
        if (group.isNotEmpty()) {
            val minTop = group.minOf { it.offset }.toFloat()
            val maxTop = (group.maxOf { it.offset + it.size } - dragging.size).toFloat()
            val top = (initialOffset + draggedDelta).coerceIn(minTop, maxOf(minTop, maxTop))
            draggedDelta = top - initialOffset
        }

        if (waitingForLayoutFrom == dragging.offset) return
        waitingForLayoutFrom = null

        val middle = initialOffset + draggedDelta + dragging.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != dragging.key &&
                    middle >= item.offset && middle <= item.offset + item.size &&
                    sameGroup(dragging.key, item.key)
        } ?: return

        if (dragging.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
        if (onMove(dragging.key, target.key)) waitingForLayoutFrom = dragging.offset
    }

    fun end() {
        if (draggingKey == null) return
        val info = draggingInfo
        onDrop()
        if (info == null) {
            draggingKey = null
            draggedDelta = 0f
            return
        }
        // Slide the row into its slot, then let go of it
        val settleAt = (info.offset - initialOffset).toFloat()
        scope.launch {
            animate(draggedDelta, settleAt, animationSpec = tween(150)) { value, _ -> draggedDelta = value }
            draggingKey = null
            draggedDelta = 0f
        }
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    sameGroup: (Any, Any) -> Boolean,
    onMove: (Any, Any) -> Boolean,
    onDrop: () -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val latestMove by rememberUpdatedState(onMove)
    val latestDrop by rememberUpdatedState(onDrop)
    return remember(listState) {
        ReorderState(listState, scope, sameGroup, { a, b -> latestMove(a, b) }, { latestDrop() })
    }
}

// A row that can be dragged. `content` receives the modifier to put on the drag handle.
@Composable
fun LazyItemScope.ReorderRow(
    reorder: ReorderState,
    key: Any,
    content: @Composable (handle: Modifier) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val dragging = reorder.draggingKey == key

    val rowModifier = if (dragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = reorder.draggingOffset }
    } else {
        Modifier.animateItem()
    }

    val handle = Modifier.pointerInput(key) {
        detectDragGestures(
            onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                reorder.start(key)
            },
            onDrag = { change, amount ->
                change.consume()
                reorder.drag(amount.y)
            },
            onDragEnd = { reorder.end() },
            onDragCancel = { reorder.end() }
        )
    }

    Surface(
        modifier = rowModifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        shadowElevation = if (dragging) 8.dp else 0.dp
    ) {
        content(handle)
    }
}