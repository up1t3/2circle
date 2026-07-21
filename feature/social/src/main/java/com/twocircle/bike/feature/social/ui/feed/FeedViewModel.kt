package com.twocircle.bike.feature.social.ui.feed

import androidx.lifecycle.ViewModel
import com.twocircle.bike.domain.model.Coord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor() : ViewModel() {

    private val _feedItems = MutableStateFlow(
        listOf(
            FeedItem(
                id = "1",
                authorName = "Екатерина Соколова",
                timeAgo = "2 часа назад",
                title = "Вечерний круг вокруг озера Шарташ",
                distance = "24.5 км",
                duration = "1 ч 15 мин",
                ascent = "180 м",
                points = listOf(
                    Coord(56.84, 60.67), Coord(56.85, 60.69),
                    Coord(56.86, 60.71), Coord(56.84, 60.72),
                    Coord(56.83, 60.68), Coord(56.84, 60.67)
                ),
                kudosCount = 14,
                isKudosGiven = false,
                commentsCount = 3,
            ),
            FeedItem(
                id = "2",
                authorName = "Михаил Громов",
                timeAgo = "5 часов назад",
                title = "Заезд по гравию на Волчиху",
                distance = "52.0 км",
                duration = "2 ч 40 мин",
                ascent = "520 м",
                points = listOf(
                    Coord(56.80, 60.50), Coord(56.82, 60.40),
                    Coord(56.85, 60.30), Coord(56.83, 60.45),
                    Coord(56.80, 60.50)
                ),
                kudosCount = 28,
                isKudosGiven = true,
                commentsCount = 8,
            )
        )
    )
    val feedItems: StateFlow<List<FeedItem>> = _feedItems.asStateFlow()

    fun toggleKudos(itemId: String) {
        _feedItems.value = _feedItems.value.map { item ->
            if (item.id == itemId) {
                val newStatus = !item.isKudosGiven
                item.copy(
                    isKudosGiven = newStatus,
                    kudosCount = if (newStatus) item.kudosCount + 1 else item.kudosCount - 1
                )
            } else item
        }
    }
}
