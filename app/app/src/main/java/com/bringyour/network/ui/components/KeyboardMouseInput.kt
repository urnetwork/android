package com.bringyour.network.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Keyboard, mouse and trackpad support, after Tier 2 of Google's adaptive app
 * quality guidelines (developer.android.com/docs/quality-guidelines/
 * adaptive-app-quality/tier-2): a ring on the element that has keyboard focus
 * (Drawable_Focus), Esc as one press (Keyboard_Exit), context menus on a
 * right click or the context menu key (Context_Menus), and keyboard scrolling
 * for a list with nothing in it to focus (Keyboard_Navigation). The key rules
 * are pure, for the JVM tests.
 */

// ---------------------------------------------------------------- focus ring

/** The focus ring's color: the theme's secondary, light on every dark surface. */
val FocusRingColor: Color = BlueLight

/** The focus ring's stroke. */
val FocusRingWidth: Dp = 2.dp

/**
 * A ring around the element while it has keyboard focus (Drawable_Focus). It
 * follows the focus target this modifier observes: the next one inward (the
 * clickable, toggleable or focusable after it), else the first among its
 * children. A clickable takes focus only outside touch mode, so the ring is
 * the keyboard's and never shows for a tap. The ring's outer edge is [outset]
 * outside the element's bounds; zero keeps it inside them, for an element its
 * container clips. Place it before the element's own clip.
 */
fun Modifier.focusRing(
    shape: Shape = RectangleShape,
    outset: Dp = 0.dp,
    color: Color = FocusRingColor,
    width: Dp = FocusRingWidth,
): Modifier = this then FocusRingElement(shape, outset, color, width)

private data class FocusRingElement(
    val shape: Shape,
    val outset: Dp,
    val color: Color,
    val width: Dp,
) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape, outset, color, width)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.outset = outset
        node.color = color
        node.width = width
        node.invalidateDraw()
    }
}

private class FocusRingNode(
    var shape: Shape,
    var outset: Dp,
    var color: Color,
    var width: Dp,
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focused != focusState.isFocused) {
            focused = focusState.isFocused
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) {
            return
        }
        val stroke = width.toPx()
        // the stroke's center line lies half a stroke in from the ring's outer edge
        val grow = outset.toPx() - stroke / 2f
        val ring = Size(size.width + 2f * grow, size.height + 2f * grow)
        if (ring.width <= 0f || ring.height <= 0f) {
            return
        }
        val outline = shape.createOutline(ring, layoutDirection, this)
        translate(left = -grow, top = -grow) {
            drawOutline(outline, color, style = Stroke(width = stroke))
        }
    }
}

// ----------------------------------------------------------------------- Esc

/**
 * Esc as one press (Keyboard_Exit): [onEscape] runs on the key down and says
 * whether it acted. The key up of a press it acted on, and the repeats of a
 * held key, are consumed with it, so nothing else sees half of the press. An
 * Esc it leaves goes on to the system, which reads it as Back: a dialog, a
 * sheet or a menu closes, a screen goes back.
 */
fun Modifier.onEscape(onEscape: () -> Boolean): Modifier = this then EscapeElement(onEscape)

private data class EscapeElement(val onEscape: () -> Boolean) : ModifierNodeElement<EscapeNode>() {
    override fun create() = EscapeNode(onEscape)

    override fun update(node: EscapeNode) {
        node.onEscape = onEscape
    }
}

private class EscapeNode(var onEscape: () -> Boolean) : Modifier.Node(), KeyInputModifierNode {
    private var acted = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.Escape) {
            return false
        }
        return when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    acted = onEscape()
                }
                acted
            }
            KeyEventType.KeyUp -> acted.also { acted = false }
            else -> false
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false
}

/** What Esc does in a search field (Keyboard_Exit): it clears the search, then leaves the field. */
enum class SearchEscape { ClearSearch, LeaveField }

fun searchEscape(query: String): SearchEscape =
    if (query.isNotEmpty()) SearchEscape.ClearSearch else SearchEscape.LeaveField

// -------------------------------------------------------------- context menu

/**
 * One action of a context menu. It is always an action the element already
 * offers another way (its click, a swipe, a touch and hold, a screen's
 * button), so a menu adds a way in and never a feature.
 */
@Immutable
data class ContextMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * The keys that open the focused element's context menu, as on a desktop: the
 * context menu key (Android's MENU) and Shift+F10, and Enter as well when
 * [enterOpens], for an element that takes focus but has no click of its own.
 */
fun isContextMenuKey(key: Key, isShiftPressed: Boolean, enterOpens: Boolean = false): Boolean =
    key == Key.Menu ||
        (key == Key.F10 && isShiftPressed) ||
        (enterOpens && (key == Key.Enter || key == Key.NumPadEnter))

/** Where a context menu opened from the keyboard starts: under the element, in from its start. */
private val KeyboardMenuInset = 16.dp

/**
 * [content] with a context menu of [items] (Context_Menus). A right click,
 * the secondary mouse button or a trackpad's secondary tap, opens it at the
 * pointer, beside what was clicked; the context menu key or Shift+F10 opens it
 * under the element while keyboard focus is inside it, with its first action
 * focused, and so does Enter with [openOnEnter]. The right click never
 * reaches the element's own click. A choice, Esc or a click outside closes the
 * menu. With no items there is no menu.
 */
@Composable
fun ContextMenuBox(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    openOnEnter: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    var openedByKeyboard by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val currentItems by rememberUpdatedState(items)
    val layoutDirection = LocalLayoutDirection.current
    val keyboardInsetPx = with(LocalDensity.current) { KeyboardMenuInset.roundToPx() }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            // the initial pass sees the press before the element's own click,
            // which then finds it consumed and does nothing
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (
                            event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed &&
                            currentItems.isNotEmpty()
                        ) {
                            event.changes.forEach { it.consume() }
                            anchor = event.changes.first().position.round()
                            openedByKeyboard = false
                            expanded = true
                        }
                    }
                }
            }
            .onKeyEvent { event ->
                if (currentItems.isEmpty() || !isContextMenuKey(event.key, event.isShiftPressed, openOnEnter)) {
                    return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    anchor = IntOffset(
                        x = if (layoutDirection == LayoutDirection.Ltr) {
                            keyboardInsetPx
                        } else {
                            boxSize.width - keyboardInsetPx
                        },
                        y = boxSize.height,
                    )
                    openedByKeyboard = true
                    expanded = true
                }
                true
            }
    ) {
        content()

        if (items.isNotEmpty()) {
            // the point the menu opens from, in absolute coordinates like the
            // pointer's; the menu itself mirrors for right to left
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { anchor }
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = MainTintedBackgroundBase,
                ) {
                    val firstItem = remember { FocusRequester() }
                    items.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (item.destructive) Red else Color.Unspecified,
                                )
                            },
                            onClick = {
                                expanded = false
                                item.onClick()
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                        )
                    }
                    if (openedByKeyboard) {
                        LaunchedEffect(Unit) {
                            // the menu's window may not have taken focus yet; Down
                            // still reaches the first action then
                            runCatching { firstItem.requestFocus() }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ keyboard scroll

/** A keyboard scroll of a list (Keyboard_Navigation). */
enum class KeyboardScroll { LineUp, LineDown, PageUp, PageDown, Top, Bottom }

/**
 * The scroll a key asks of a list: the arrows a line, Page Up and Page Down a
 * page (Space and Shift+Space too, as in a browser), Home and End the ends.
 * Null for any other key, which goes on to focus navigation.
 */
fun keyboardScrollFor(key: Key, isShiftPressed: Boolean): KeyboardScroll? = when (key) {
    Key.DirectionUp -> KeyboardScroll.LineUp
    Key.DirectionDown -> KeyboardScroll.LineDown
    Key.PageUp -> KeyboardScroll.PageUp
    Key.PageDown -> KeyboardScroll.PageDown
    Key.Spacebar -> if (isShiftPressed) KeyboardScroll.PageUp else KeyboardScroll.PageDown
    Key.MoveHome -> KeyboardScroll.Top
    Key.MoveEnd -> KeyboardScroll.Bottom
    else -> null
}

/** The share of the viewport a page scroll moves, so a line of context stays in view. */
const val KEYBOARD_PAGE_FRACTION = 0.875f

/**
 * How far [scroll] moves a list whose viewport is [viewportPx] tall, positive
 * toward the end; null for the jumps to either end.
 */
fun keyboardScrollDistance(scroll: KeyboardScroll, viewportPx: Float, linePx: Float): Float? =
    when (scroll) {
        KeyboardScroll.LineUp -> -linePx
        KeyboardScroll.LineDown -> linePx
        KeyboardScroll.PageUp -> -viewportPx * KEYBOARD_PAGE_FRACTION
        KeyboardScroll.PageDown -> viewportPx * KEYBOARD_PAGE_FRACTION
        KeyboardScroll.Top, KeyboardScroll.Bottom -> null
    }

/**
 * Lets the keyboard scroll a list whose rows take no focus, such as the
 * leaderboard's: Tab gives the list itself focus, with the ring, and the keys
 * of [keyboardScrollFor] scroll it, also while one of its few focusable
 * controls (a switch, the sort) has focus. Tab still reaches those controls.
 * Not for a list of focusable rows, whose arrows move focus between them.
 */
fun Modifier.keyboardScrollable(state: LazyListState, scope: CoroutineScope, linePx: Float): Modifier =
    this
        .focusRing()
        .focusTarget()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                return@onKeyEvent false
            }
            val scroll = keyboardScrollFor(event.key, event.isShiftPressed) ?: return@onKeyEvent false
            scope.launch {
                val viewportPx = state.layoutInfo.viewportSize.height.toFloat()
                val distance = keyboardScrollDistance(scroll, viewportPx, linePx)
                when {
                    distance != null -> state.animateScrollBy(distance)
                    scroll == KeyboardScroll.Top -> state.scrollToItem(0)
                    else -> state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                }
            }
            true
        }
