package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.feature.timer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** Порог свайпа. Ниже — случайное смещение пальца при тапе. */
private val SWIPE_THRESHOLD = 48.dp

/**
 * С какого смещения жест считается направленным.
 *
 * До него направление ещё не выбрано: у почти нулевого сдвига «больше по x или
 * по y» решает дрожание пальца, и страница-подсказка металась бы между
 * «следующий этап» и «выход». Четверть порога — уже движение, но ещё далеко от
 * срабатывания.
 */
private val PEEK_THRESHOLD = SWIPE_THRESHOLD / 4

/**
 * Насколько содержимое идёт за пальцем.
 *
 * Не единица: экран должен отзываться на жест, но не уезжать целиком от
 * небрежного движения. Половина хода — общая мера «тянется, но упирается»,
 * по которой жест читается как обратимый.
 */
private const val DRAG_FOLLOW = 0.5f

/** Куда уезжает содержимое, когда жест сработал. */
private val COMMIT_SHIFT = 96.dp

/** Насколько бледнеет уезжающее содержимое. Ноль был бы морганием. */
private const val MIN_ALPHA = 0.2f

/**
 * На каком **сдвиге** жест уже сработал бы.
 *
 * Порог задан ходом пальца, а содержимое идёт за ним вполсилы, поэтому
 * сдвига вдвое меньше. Пересчёт нужен затуханию: обе страницы меняют
 * прозрачность не «куда-нибудь за 96 dp», а ровно к моменту срабатывания —
 * полностью проявившаяся подсказка и означает «отпускай, приедет она».
 */
private fun triggerShift(thresholdPx: Float): Float = thresholdPx * DRAG_FOLLOW

private const val COMMIT_MS = 130
private const val RETURN_MS = 220

/**
 * Рабочая область режима фокуса: жесты и их анимация
 * (docs/03-GESTURES.md §3, замечания 3 и 6 полевой проверки 2026-08-05).
 *
 * Жесты и анимация живут вместе не для красоты: анимация здесь и есть ответ
 * жеста. В фокусе нет ни кнопок, ни ряби нажатия, и свайп вслепую — единственный
 * способ управления; без отклика инструктор не знает, сработал он или палец
 * прошёл мимо порога, и делает второй свайп поверх первого.
 *
 * Карта жестов:
 * - **свайп ← →** — следующий и предыдущий этап; содержимое уезжает в сторону
 *   свайпа и въезжает с противоположной, как страница;
 * - **свайп ↑ ↓** — выход из фокуса; содержимое уходит в сторону жеста;
 * - **тап** — выход из фокуса;
 * - **двойной тап** — пауза и продолжение.
 *
 * **За уезжающим содержимым видно то, что приедет** (замечание 3 полевой
 * проверки 2026-08-05). Раньше под ним была пустота: страница листалась, но
 * листалась в никуда, и до отпускания пальца жест ничего не обещал — а свайп в
 * фокусе делают вслепую, с коврика, и «что будет, если отпустить» это и есть
 * главный вопрос. Теперь с противоположной стороны въезжает страница
 * [FocusPeek] — следующий этап, предыдущий или обычный экран, — и жест можно
 * передумать, не отпуская.
 *
 * Пауза переехала на двойной тап со свайпа вниз (замечание 3): свайп вниз для
 * паузы противоречил тому, чего от него ждут — «убрать, свернуть, выйти», — и
 * половина попыток выйти из фокуса вместо этого останавливала занятие. Двойной
 * тап под это подходит: случайно его не сделать, а промахнуться мимо экрана
 * невозможно. Карта жестов до 2026-08-05 запрещала двойной тап вовсе, но
 * запрет был про **разблокировку** — там он ненадёжен, потому что телефон
 * лежит на коврике среди занимающихся. В фокусе телефон в руках или на виду, и
 * это другая история.
 *
 * @param onInteraction любое касание: сбрасывает отсчёт до автозатемнения.
 * @param peek страница, которая приедет: рисуется под уезжающим содержимым.
 */
@Composable
internal fun FocusArea(
    onExit: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePause: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    peek: @Composable (FocusPeek) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    val peekThresholdPx = with(density) { PEEK_THRESHOLD.toPx() }
    val shiftPx = with(density) { COMMIT_SHIFT.toPx() }

    val shiftX = remember { Animatable(0f) }
    val shiftY = remember { Animatable(0f) }

    // Что приедет, если отпустить сейчас. `null` — жест ещё ни о чём не
    // говорит либо уже отработал; вернувшаяся пружиной подсказка гаснет сама,
    // потому что её прозрачность считается из смещения.
    var target by remember { mutableStateOf<FocusPeek?>(null) }

    val exitLabel = stringResource(R.string.timer_focus_exit)
    val nextLabel = stringResource(R.string.timer_action_next_stage)
    val previousLabel = stringResource(R.string.timer_action_previous_stage)
    val pauseLabel = stringResource(R.string.timer_pause)

    Box(
        modifier =
            modifier
                // В ключах — всё, что блок замыкает: обработчик, оставшийся от
                // прошлой композиции, звал бы уже недействующее действие. Сами
                // лямбды стабильны (Compose запоминает их), поэтому жест
                // посреди свайпа не обрывается пересозданием блока.
                .pointerInput(onNext, onPrevious, onExit, onInteraction, thresholdPx, shiftPx) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            total = Offset.Zero
                            onInteraction()
                        },
                        onDrag = { _, delta ->
                            total += delta
                            target = peekFor(total, peekThresholdPx)
                            scope.launch {
                                shiftX.snapTo(total.x * DRAG_FOLLOW)
                                shiftY.snapTo(total.y * DRAG_FOLLOW)
                            }
                        },
                        // Направление считается по всему жесту, а не по первым
                        // пикселям: палец на коврике идёт по дуге, и решение по
                        // началу движения ошибается.
                        onDragEnd = {
                            when {
                                abs(total.x) > abs(total.y) && abs(total.x) > thresholdPx -> {
                                    scope.commitSideways(shiftX, shiftY, shiftPx, forward = total.x < 0) {
                                        if (total.x < 0) onNext() else onPrevious()
                                        // Страница-подсказка уходит вместе с
                                        // тем, что она обещала: дальше на её
                                        // месте уже новый этап.
                                        target = null
                                    }
                                }

                                abs(total.y) > thresholdPx -> {
                                    scope.commitAway(shiftX, shiftY, shiftPx, up = total.y < 0, onExit)
                                }

                                else -> {
                                    scope.springBack(shiftX, shiftY)
                                }
                            }
                        },
                        onDragCancel = { scope.springBack(shiftX, shiftY) },
                    )
                }.pointerInput(onExit, onTogglePause, onInteraction) {
                    detectTapGestures(
                        // Двойной тап проверяется первым самой Compose: одиночный
                        // придёт только после того, как второго не дождались.
                        onDoubleTap = {
                            onInteraction()
                            onTogglePause()
                        },
                        onTap = {
                            onInteraction()
                            onExit()
                        },
                    )
                }.semantics {
                    // Свайпов для TalkBack не существует: там жесты забраны
                    // системой под навигацию по элементам. Те же действия
                    // объявлены словами (docs/03-GESTURES.md §7).
                    role = Role.Button
                    onClick(label = exitLabel) {
                        onExit()
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(nextLabel) {
                                onNext()
                                true
                            },
                            CustomAccessibilityAction(previousLabel) {
                                onPrevious()
                                true
                            },
                            CustomAccessibilityAction(pauseLabel) {
                                onTogglePause()
                                true
                            },
                        )
                },
    ) {
        // Приезжающая страница — под уезжающей и ровно на шаг в стороне, с
        // которой она придёт. Проявляется настолько, насколько бледнеет
        // текущая: обе доли считаются из одного смещения, поэтому разойтись
        // им негде.
        target?.let { peekTarget ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val step = if (peekTarget.horizontal) shiftX.value else shiftY.value
                            val offset = step + shiftPx * if (step < 0f) 1f else -1f
                            translationX = if (peekTarget.horizontal) offset else 0f
                            translationY = if (peekTarget.horizontal) 0f else offset
                            alpha = (abs(step) / triggerShift(thresholdPx)).coerceIn(0f, 1f)
                        }
                        // Для TalkBack этой страницы нет: свайпов там не
                        // существует, а те же действия объявлены словами.
                        .clearAndSetSemantics {},
            ) {
                peek(peekTarget)
            }
        }

        Box(
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = shiftX.value
                    translationY = shiftY.value
                    // Прозрачность считается из смещения, а не анимируется
                    // отдельно: одно движение — один источник, и рассинхрону
                    // взяться неоткуда.
                    alpha = fadeFor(hypot(shiftX.value, shiftY.value), triggerShift(thresholdPx))
                },
        ) {
            content()
        }
    }
}

/**
 * Что приедет, если отпустить палец при таком смещении.
 *
 * Ось выбирается по большей составляющей — тем же правилом, что и на конце
 * жеста: иначе подсказка обещала бы одно, а срабатывало бы другое.
 */
private fun peekFor(
    total: Offset,
    thresholdPx: Float,
): FocusPeek? =
    when {
        hypot(total.x, total.y) < thresholdPx -> null
        abs(total.x) > abs(total.y) -> if (total.x < 0) FocusPeek.NEXT else FocusPeek.PREVIOUS
        else -> FocusPeek.EXIT
    }

private fun fadeFor(
    distancePx: Float,
    spanPx: Float,
): Float = (1f - (1f - MIN_ALPHA) * (distancePx / spanPx)).coerceIn(MIN_ALPHA, 1f)

/**
 * Смена этапа: содержимое уезжает в сторону свайпа и въезжает с противоположной.
 *
 * Этап переключается в середине, когда старое уже ушло, а новое ещё не
 * появилось: иначе на въезде видно, как цифры меняются сами по себе.
 */
private fun CoroutineScope.commitSideways(
    shiftX: Animatable<Float, AnimationVector1D>,
    shiftY: Animatable<Float, AnimationVector1D>,
    shiftPx: Float,
    forward: Boolean,
    action: () -> Unit,
) {
    launch {
        launch { shiftY.animateTo(0f, tween(COMMIT_MS)) }
        shiftX.animateTo(if (forward) -shiftPx else shiftPx, tween(COMMIT_MS))
        action()
        shiftX.snapTo(if (forward) shiftPx else -shiftPx)
        shiftX.animateTo(0f, tween(RETURN_MS))
    }
}

/** Выход из фокуса: содержимое уходит в сторону жеста и уступает место экрану. */
private fun CoroutineScope.commitAway(
    shiftX: Animatable<Float, AnimationVector1D>,
    shiftY: Animatable<Float, AnimationVector1D>,
    shiftPx: Float,
    up: Boolean,
    onExit: () -> Unit,
) {
    launch {
        launch { shiftX.animateTo(0f, tween(COMMIT_MS)) }
        shiftY.animateTo(if (up) -shiftPx else shiftPx, tween(COMMIT_MS))
        onExit()
    }
}

/**
 * Жест не дотянул до порога: содержимое возвращается пружиной — «не сработало».
 *
 * Страницу-подсказку гасить не нужно: её прозрачность считается из того же
 * смещения, и пружина уводит её вместе с содержимым. Отдельный сигнал «убрать»
 * пришлось бы гасить в конце анимации — то есть в коде, который переживает
 * начало следующего жеста и однажды погасил бы уже его подсказку.
 */
private fun CoroutineScope.springBack(
    shiftX: Animatable<Float, AnimationVector1D>,
    shiftY: Animatable<Float, AnimationVector1D>,
) {
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    launch { shiftX.animateTo(0f, spec) }
    launch { shiftY.animateTo(0f, spec) }
}

/**
 * Страница, которая приедет на место текущей.
 *
 * Знает только о направлении — что на ней написать, решает рабочий экран:
 * область жестов не должна знать ни про этапы, ни про кнопки.
 */
internal enum class FocusPeek {
    /** Свайп влево: следующий этап, а на последнем — конец занятия. */
    NEXT,

    /** Свайп вправо: предыдущий этап. */
    PREVIOUS,

    /** Свайп вверх или вниз: обычный экран с кнопками. */
    EXIT,
    ;

    /** По какой оси эта страница приезжает. */
    val horizontal: Boolean get() = this != EXIT
}
