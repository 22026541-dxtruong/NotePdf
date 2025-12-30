package ie.app.notepdf.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.node.*
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.*
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// --- PHẦN 1: FAB MENU (Dọc) ---
@Composable
fun FloatingActionButtonMenu(
    expanded: Boolean,
    button: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    content: @Composable FloatingActionButtonMenuScope.() -> Unit,
) {
    var buttonHeight by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    Layout(
        modifier = modifier.padding(horizontal = FabMenuPaddingHorizontal),
        content = {
            FloatingActionButtonMenuItemColumn(
                Modifier.focusRequester(focusRequester),
                expanded,
                horizontalAlignment,
                { buttonHeight },
                content,
            )
            Box(
                Modifier.onKeyEvent {
                    if (expanded && it.type == KeyEventType.KeyDown &&
                        ((it.key == Key.Tab && !it.isShiftPressed) || it.key == Key.DirectionDown)
                    ) {
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
            ) { button() }
        },
    ) { measureables, constraints ->
        val buttonPlaceable = measureables[1].measure(constraints)
        buttonHeight = buttonPlaceable.height
        val menuItemsPlaceable = measureables[0].measure(constraints)
        val buttonPaddingBottom = FabMenuButtonPaddingBottom.roundToPx()
        val suggestedWidth = maxOf(buttonPlaceable.width, menuItemsPlaceable.width)
        val suggestedHeight = maxOf(buttonPlaceable.height + buttonPaddingBottom, menuItemsPlaceable.height)
        val width = minOf(suggestedWidth, constraints.maxWidth)
        val height = minOf(suggestedHeight, constraints.maxHeight)
        layout(width, height) {
            val menuItemsX = horizontalAlignment.align(menuItemsPlaceable.width, width, layoutDirection)
            menuItemsPlaceable.place(menuItemsX, 0)
            val buttonX = horizontalAlignment.align(buttonPlaceable.width, width, layoutDirection)
            val buttonY = height - buttonPlaceable.height - buttonPaddingBottom
            buttonPlaceable.place(buttonX, buttonY)
        }
    }
}

@Composable
private fun FloatingActionButtonMenuItemColumn(
    modifier: Modifier,
    expanded: Boolean,
    horizontalAlignment: Alignment.Horizontal,
    buttonHeight: () -> Int,
    content: @Composable FloatingActionButtonMenuScope.() -> Unit,
) {
    var itemCount by remember { mutableIntStateOf(0) }
    var itemsNeedVerticalScroll by remember { mutableStateOf(false) }
    var originalConstraints: Constraints? = null
    var staggerAnim by remember { mutableStateOf<Animatable<Int, AnimationVector1D>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var staggerAnimSpec: FiniteAnimationSpec<Int> = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.8f)

    Layout(
        modifier = modifier.clipToBounds()
            .semantics { isTraversalGroup = true; traversalIndex = -0.9f }
            .layout { measurable, constraints ->
                originalConstraints = constraints
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .then(if (itemsNeedVerticalScroll) Modifier.verticalScroll(rememberScrollState(), enabled = expanded) else Modifier),
        content = {
            val scope = remember(horizontalAlignment) {
                object : FloatingActionButtonMenuScope { override val horizontalAlignment: Alignment.Horizontal get() = horizontalAlignment }
            }
            content(scope)
        },
    ) { measurables, constraints ->
        itemCount = measurables.size
        val targetItemCount = if (expanded) itemCount else 0
        staggerAnim = staggerAnim?.also {
            if (it.targetValue != targetItemCount) {
                coroutineScope.launch { it.animateTo(targetValue = targetItemCount, animationSpec = staggerAnimSpec) }
            }
        } ?: Animatable(targetItemCount, Int.VectorConverter)

        val placeables = measurables.fastMap { measurable -> measurable.measure(constraints) }
        val width = placeables.fastMaxBy { it.width }?.width ?: 0
        val verticalSpacing = FabMenuItemSpacingVertical.roundToPx()
        val verticalSpacingHeight = if (placeables.isNotEmpty()) verticalSpacing * (placeables.size - 1) else 0
        val currentButtonHeight = buttonHeight()
        val bottomPadding = if (currentButtonHeight > 0) currentButtonHeight + FabMenuButtonPaddingBottom.roundToPx() + FabMenuPaddingBottom.roundToPx() else 0
        val height = placeables.fastSumBy { it.height } + verticalSpacingHeight + bottomPadding
        var visibleHeight = bottomPadding.toFloat()
        placeables.fastForEachIndexed { index, placeable ->
            val itemVisible = index >= itemCount - (staggerAnim?.value ?: 0)
            if (itemVisible) {
                visibleHeight += placeable.height
                if (index < placeables.size - 1) visibleHeight += verticalSpacing
            }
        }
        val finalHeight = if (placeables.fastAny { item -> item.isVisible }) height else 0
        itemsNeedVerticalScroll = finalHeight > (originalConstraints?.maxHeight ?: 0)
        layout(width, finalHeight, rulers = { MenuItemRuler provides height - visibleHeight }) {
            var y = 0
            placeables.fastForEachIndexed { index, placeable ->
                val x = horizontalAlignment.align(placeable.width, width, layoutDirection)
                placeable.place(x, y)
                y += placeable.height
                if (index < placeables.size - 1) y += verticalSpacing
            }
        }
    }
}

interface FloatingActionButtonMenuScope { val horizontalAlignment: Alignment.Horizontal }
private val MenuItemRuler = HorizontalRuler()

@Composable
fun FloatingActionButtonMenuScope.FloatingActionButtonMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor),
) {
    var widthAnim by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
    var alphaAnim by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
    val widthSpring: FiniteAnimationSpec<Float> = spring(stiffness = 400f, dampingRatio = 0.8f)
    val alphaSpring: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 1f)
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            modifier = modifier.itemVisible({ isVisible }).layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val target = if (MenuItemRuler.current(Float.POSITIVE_INFINITY) <= 0) 1f else 0f
                    widthAnim = widthAnim?.also {
                        if (it.targetValue != target) coroutineScope.launch { it.animateTo(target, widthSpring) }
                    } ?: Animatable(target, Float.VectorConverter)
                    val tempAlphaAnim = alphaAnim?.also {
                        if (it.targetValue != target) coroutineScope.launch { it.animateTo(target, alphaSpring) }
                    } ?: Animatable(target, Float.VectorConverter)
                    alphaAnim = tempAlphaAnim
                    isVisible = tempAlphaAnim.value != 0f
                    if (isVisible) placeable.placeWithLayer(0, 0) { alpha = tempAlphaAnim.value }
                }
            },
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
            onClick = onClick,
        ) {
            Row(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val width = (placeable.width * maxOf((widthAnim?.value ?: 0f), 0f)).roundToInt()
                    layout(width, placeable.height) {
                        val x = horizontalAlignment.align(placeable.width, width, layoutDirection)
                        placeable.placeWithLayer(x, 0)
                    }
                }.sizeIn(minWidth = FabMenuItemMinWidth, minHeight = FabMenuItemHeight)
                    .padding(start = FabMenuItemContentPaddingStart, end = FabMenuItemContentPaddingEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FabMenuItemContentSpacingHorizontal, Alignment.CenterHorizontally),
            ) { icon(); CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium, content = text) }
        }
    }
}

// --- PHẦN 2: TOGGLE FAB (Dùng chung) ---
@Composable
fun ToggleFloatingActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    targetWidth: Dp = FabInitialSize,
    contentClickable: Boolean = true,
    containerColor: (Float) -> Color = ToggleFloatingActionButtonDefaults.containerColor(),
    contentAlignment: Alignment = Alignment.TopEnd,
    containerSize: (Float) -> Dp = ToggleFloatingActionButtonDefaults.containerSize(),
    containerCornerRadius: (Float) -> Dp = ToggleFloatingActionButtonDefaults.containerCornerRadius(),
    content: @Composable ToggleFloatingActionButtonScope.() -> Unit,
) {
    val checkedProgress = animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = 400f, dampingRatio = 0.8f))
    val widthState = animateDpAsState(targetWidth, spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.8f))

    ToggleFloatingActionButtonImpl(
        checked, onCheckedChange, { checkedProgress.value }, { widthState.value }, contentClickable,
        modifier, containerColor, contentAlignment, containerSize, containerCornerRadius, content,
    )
}

@Composable
private fun ToggleFloatingActionButtonImpl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedProgress: () -> Float,
    currentWidth: () -> Dp,
    contentClickable: Boolean,
    modifier: Modifier,
    containerColor: (Float) -> Color,
    contentAlignment: Alignment,
    containerSize: (Float) -> Dp,
    containerCornerRadius: (Float) -> Dp,
    content: @Composable ToggleFloatingActionButtonScope.() -> Unit,
) {
    val initialSize = remember(containerSize) { containerSize(0f) }
    Box(Modifier.size(width = currentWidth(), height = initialSize), contentAlignment = contentAlignment) {
        val density = LocalDensity.current
        val fabRippleRadius = remember(initialSize) { with(density) { hypot(initialSize.toPx() / 2, initialSize.toPx() / 2).toDp() } }
        val shape = remember(density, checkedProgress, containerCornerRadius) {
            GenericShape { size, _ ->
                val radius = with(density) { containerCornerRadius(checkedProgress()).toPx() }
                addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
            }
        }

        Box(
            modifier.graphicsLayer { shadowElevation = FabShadowElevation.toPx(); this.shape = shape; clip = true }
                .drawBehind {
                    val radius = with(density) { containerCornerRadius(checkedProgress()).toPx() }
                    drawRoundRect(color = containerColor(checkedProgress()), cornerRadius = CornerRadius(radius))
                }
                .then(if (contentClickable) Modifier.toggleable(value = checked, onValueChange = onCheckedChange, interactionSource = null, indication = ripple(radius = fabRippleRadius)) else Modifier)
                .layout { measurable, constraints ->
                    val heightPx = containerSize(checkedProgress()).roundToPx()
                    val widthPx = currentWidth().roundToPx()
                    layout(widthPx, heightPx) {
                        val placeable = measurable.measure(constraints)
                        val x = contentAlignment.align(IntSize(placeable.width, placeable.height), IntSize(widthPx, heightPx), layoutDirection).x
                        val y = contentAlignment.align(IntSize(placeable.width, placeable.height), IntSize(widthPx, heightPx), layoutDirection).y
                        placeable.place(x, y)
                    }
                }
        ) {
            val scope = remember(checkedProgress) { object : ToggleFloatingActionButtonScope { override val checkedProgress: Float get() = checkedProgress() } }
            content(scope)
        }
    }
}

// --- Defaults ---
object ToggleFloatingActionButtonDefaults {
    @Composable fun containerColor(initialColor: Color = MaterialTheme.colorScheme.primaryContainer, finalColor: Color = MaterialTheme.colorScheme.secondaryContainer): (Float) -> Color = { progress -> lerp(initialColor, finalColor, progress) }
    fun containerSize(initialSize: Dp = FabInitialSize, finalSize: Dp = FabFinalSize): (Float) -> Dp = { progress -> lerp(initialSize, finalSize, progress) }
    fun containerCornerRadius(initialSize: Dp = FabInitialCornerRadius, finalSize: Dp = FabFinalCornerRadius): (Float) -> Dp = { progress -> lerp(initialSize, finalSize, progress) }
    @Composable fun iconColor(initialColor: Color = MaterialTheme.colorScheme.onPrimaryContainer, finalColor: Color = MaterialTheme.colorScheme.onSecondaryContainer): (Float) -> Color = { progress -> lerp(initialColor, finalColor, progress) }
    fun iconSize(initialSize: Dp = FabInitialIconSize, finalSize: Dp = FabFinalIconSize): (Float) -> Dp = { progress -> lerp(initialSize, finalSize, progress) }
    @Composable fun Modifier.animateIcon(checkedProgress: () -> Float, color: (Float) -> Color = iconColor(), size: (Float) -> Dp = iconSize()) = this
        .layout { measurable, _ ->
            val sizePx = size(checkedProgress()).roundToPx()
            val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))
            layout(sizePx, sizePx) { placeable.place(0, 0) }
        }
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            layer.apply { record { drawContent() }; colorFilter = ColorFilter.tint(color(checkedProgress())) }
            onDrawWithContent { drawLayer(graphicsLayer = layer) }
        }
}
interface ToggleFloatingActionButtonScope { val checkedProgress: Float }
@Stable private fun Modifier.itemVisible(isVisible: () -> Boolean) = this then MenuItemVisibleElement(isVisible = isVisible)
private class MenuItemVisibleElement(private val isVisible: () -> Boolean) : ModifierNodeElement<MenuItemVisibilityModifier>() {
    override fun create() = MenuItemVisibilityModifier(isVisible)
    override fun update(node: MenuItemVisibilityModifier) { node.visible = isVisible }
    override fun InspectorInfo.inspectableProperties() { name = "itemVisible"; value = isVisible() }
    override fun equals(other: Any?) = (this === other) || (other is MenuItemVisibleElement && isVisible === other.isVisible)
    override fun hashCode() = isVisible.hashCode()
}
private class MenuItemVisibilityModifier(isVisible: () -> Boolean) : ParentDataModifierNode, SemanticsModifierNode, Modifier.Node() {
    var visible: () -> Boolean = isVisible
    override fun Density.modifyParentData(parentData: Any?) = this@MenuItemVisibilityModifier
    override val shouldClearDescendantSemantics get() = !visible()
    override fun SemanticsPropertyReceiver.applySemantics() {}
}
private val Placeable.isVisible get() = (this.parentData as? MenuItemVisibilityModifier)?.visible?.invoke() != false
// CONSTANTS
private val FabInitialSize = 56.dp
private val FabInitialCornerRadius = 16.dp
private val FabInitialIconSize = 24.dp
private val FabFinalSize = 56.dp
private val FabFinalCornerRadius = 28.dp
private val FabFinalIconSize = 24.dp
private val FabShadowElevation = 6.dp
private val FabMenuPaddingHorizontal = 16.dp
private val FabMenuPaddingBottom = 8.dp
private val FabMenuButtonPaddingBottom = 16.dp
private val FabMenuItemMinWidth = 56.dp
private val FabMenuItemHeight = 56.dp
private val FabMenuItemSpacingVertical = 4.dp
private val FabMenuItemContentPaddingStart = 16.dp
private val FabMenuItemContentPaddingEnd = 24.dp
private val FabMenuItemContentSpacingHorizontal = 12.dp