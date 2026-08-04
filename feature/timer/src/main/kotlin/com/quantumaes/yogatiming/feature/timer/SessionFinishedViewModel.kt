package com.quantumaes.yogatiming.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.session.SessionSummary
import com.quantumaes.yogatiming.timer.service.SessionSummarySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Модель экрана итогов.
 *
 * Итоги читает готовыми и ничего не считает: длительности сложены в момент
 * конца занятия, когда состояние движка ещё существовало
 * (`SessionController.publishSummary`). Единственное действие с побочным
 * эффектом — сохранение занятия новым профилем.
 */
@HiltViewModel
class SessionFinishedViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        summarySource: SessionSummarySource,
    ) : ViewModel() {
        /** `null` — итогов нет: приложение перезапустили после занятия. */
        val summary: StateFlow<SessionSummary?> = summarySource.lastSummary

        private val _savedProfileName = MutableStateFlow<String?>(null)

        /** Имя только что сохранённого профиля; `null` — ничего не сохраняли. */
        val savedProfileName: StateFlow<String?> = _savedProfileName.asStateFlow()

        /**
         * Сохранить занятие новым профилем (замечание 7 полевой проверки
         * 2026-08-04).
         *
         * Именно новым, а не поверх исходного: правки делались под конкретную
         * группу конкретного дня, и молча переписать ими профиль, по которому
         * инструктор ведёт ещё три занятия в неделю, — потеря его работы.
         *
         * В новый профиль уходят **эффективные** длительности, а не фактические.
         * Фактические включают всё, что случилось на занятии: досрочный переход,
         * потому что группа готова, возврат на этап, чтобы показать позу ещё
         * раз. Это события одного дня, а не план. Осознанная правка — только
         * ±30 с и сжатие плана под бюджет, и в профиль попадает ровно она.
         *
         * @param name имя нового профиля. Собирается на экране: строка
         *   локализованная, а ресурсы — не дело модели.
         */
        fun saveAdjustedProfile(name: String) {
            val finished = summary.value ?: return
            if (!finished.wasAdjusted || _savedProfileName.value != null) return
            viewModelScope.launch {
                val source = repository.getProfile(finished.profileId) ?: return@launch
                repository.saveProfile(source.copyWith(name, finished.adjustedDurationsSec))
                _savedProfileName.value = name
            }
        }
    }

/**
 * Копия профиля с новыми длительностями.
 *
 * Идентификаторы обнуляются у профиля и у каждого этапа: `saveProfile` для
 * существующего `id` делает UPDATE, и копия с чужими номерами строк переписала
 * бы исходный профиль вместо того, чтобы встать рядом. `uuid` новый по той же
 * причине — это настоящий идентификатор профиля (P1-7), и два профиля с одним
 * `uuid` схлопнутся при первом же импорте.
 *
 * Избранное не наследуется: избранным профиль делают, попользовавшись им.
 */
private fun Profile.copyWith(
    newName: String,
    durationsSec: Map<Long, Int>,
): Profile =
    copy(
        id = NEW_ID,
        uuid = UUID.randomUUID().toString(),
        name = newName,
        isFavorite = false,
        createdAt = 0,
        updatedAt = 0,
        stages = stages.map { it.copyWith(durationsSec[it.id]) },
    )

private fun Stage.copyWith(durationSec: Int?): Stage =
    copy(
        id = NEW_ID,
        profileId = NEW_ID,
        durationSec = durationSec?.coerceIn(Stage.MIN_DURATION_SEC, Stage.MAX_DURATION_SEC) ?: this.durationSec,
    )
