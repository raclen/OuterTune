package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.source.lx.KuwoSearchClient
import com.dd3boh.outertune.source.lx.toMediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = URLDecoder.decode(savedStateHandle.get<String>("query").orEmpty(), "UTF-8")
    private val _viewState = MutableStateFlow(LxSearchViewState(isLoading = true))
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            KuwoSearchClient.search(query)
                .onSuccess { songs -> _viewState.value = LxSearchViewState(items = songs.map { it.toMediaMetadata() }) }
                .onFailure { error -> _viewState.value = LxSearchViewState(error = error.message ?: "搜索失败") }
        }
    }
}

data class LxSearchViewState(
    val items: List<MediaMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
