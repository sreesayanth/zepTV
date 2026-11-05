package com.zaptv.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zaptv.core.model.PlaylistPresets
import androidx.datastore.preferences.preferencesDataStore
import com.zaptv.core.model.UserSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserSettings(
                lastChannelKey = p[LAST_CHANNEL],
                upIsNextChannel = p[UP_IS_NEXT] ?: true,
                coachSeen = p[COACH_SEEN] ?: false,
                statsVisible = p[STATS_VISIBLE] ?: false,
                enabledPlaylists = p[ENABLED_PLAYLISTS] ?: PlaylistPresets.defaultIds,
            )
        }
        .distinctUntilChanged()

    suspend fun setLastChannel(key: String) { store.edit { it[LAST_CHANNEL] = key } }
    suspend fun setUpIsNextChannel(value: Boolean) { store.edit { it[UP_IS_NEXT] = value } }
    suspend fun setCoachSeen() { store.edit { it[COACH_SEEN] = true } }
    suspend fun setStatsVisible(value: Boolean) { store.edit { it[STATS_VISIBLE] = value } }
    suspend fun setEnabledPlaylists(ids: Set<String>) { store.edit { it[ENABLED_PLAYLISTS] = ids } }

    private companion object {
        val LAST_CHANNEL = stringPreferencesKey("last_channel")
        val UP_IS_NEXT = booleanPreferencesKey("up_is_next")
        val COACH_SEEN = booleanPreferencesKey("coach_seen")
        val STATS_VISIBLE = booleanPreferencesKey("stats_visible")
        val ENABLED_PLAYLISTS = stringSetPreferencesKey("enabled_playlists")
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore
}
