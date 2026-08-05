package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.EventWithSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    /** 按播放时间倒序展示最近播放，去重后保留最近 50 首。 */
    val recentSongs = database.events()
        .map { events: List<EventWithSong> ->
            events.asSequence()
                .distinctBy { it.song.id }
                .take(50)
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
