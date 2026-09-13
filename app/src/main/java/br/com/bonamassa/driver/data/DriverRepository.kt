package br.com.bonamassa.driver.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.bonamassa.core.delivery.DriverDemo
import br.com.bonamassa.core.delivery.DriverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.driverStore by preferencesDataStore(name = "bonamassa_driver_demo_v1")

interface DriverRepository {
    val state: Flow<DriverState>
    suspend fun update(transform: (DriverState) -> DriverState): DriverState
    suspend fun reset()
}

class LocalDriverRepository(context: Context) : DriverRepository {
    private val store = context.applicationContext.driverStore
    private val key = stringPreferencesKey("driver_state")
    override val state: Flow<DriverState> = flow {
        // Persist the seed once so order timestamps survive a process restart.
        store.edit { if (it[key] == null) it[key] = DriverCodec.encode(DriverDemo.seed(System.currentTimeMillis())) }
        emitAll(store.data.map { DriverCodec.decode(requireNotNull(it[key])) })
    }

    override suspend fun update(transform: (DriverState) -> DriverState): DriverState {
        var result: DriverState? = null
        // Read, validate and write within the same transaction, including rapid taps.
        store.edit { preferences ->
            val before = DriverCodec.decode(requireNotNull(preferences[key]))
            val after = transform(before)
            preferences[key] = DriverCodec.encode(after)
            result = after
        }
        return requireNotNull(result)
    }

    override suspend fun reset() {
        store.edit { it[key] = DriverCodec.encode(DriverDemo.seed(System.currentTimeMillis())) }
    }
}
