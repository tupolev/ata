package dev.tupolev.ata

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tupolev.ata.api.Device
import dev.tupolev.ata.api.DeviceRepository
import dev.tupolev.ata.auth.SecretsImporter
import dev.tupolev.ata.auth.TokenStorage
import dev.tupolev.ata.monitor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AtaUiState(
    val authenticated: Boolean = false,
    val needsKeySetup: Boolean = false,
    val devices: List<Device> = emptyList(),
    val monitors: Map<String, TrackerConfig> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceRepository(application)
    private val tokens = TokenStorage(application)
    private val monitorStore = MonitorStore(application)

    private val _state = MutableStateFlow(
        AtaUiState(
            authenticated = repository.savedOauthToken != null || repository.hasReusableCredentials(),
            needsKeySetup = repository.savedOauthToken != null && !repository.hasSharedKey(),
        )
    )
    val state: StateFlow<AtaUiState> = _state

    init {
        if (_state.value.authenticated && !_state.value.needsKeySetup) {
            refreshDevices()
        }
    }

    fun onTokenReceived(email: String, token: String) {
        repository.saveCredentials(email, token)
        _state.value = _state.value.copy(
            authenticated = true,
            needsKeySetup = !repository.hasSharedKey(),
            error = null,
        )
        if (repository.hasSharedKey()) refreshDevices()
    }

    fun onSharedKeyReceived(key: ByteArray) {
        repository.saveSharedKey(key)
        _state.value = _state.value.copy(needsKeySetup = false, error = null)
        refreshDevices(key)
    }

    fun importSecrets(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = SecretsImporter.import(text, tokens)
                if (!result.usable) {
                    throw IllegalArgumentException(
                        "secrets.json does not contain the reusable AAS and owner keys."
                    )
                }
                _state.value = _state.value.copy(
                    authenticated = true,
                    needsKeySetup = false,
                    error = null,
                )
                refreshDevices()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not import secrets.json")
            }
        }
    }

    fun refreshDevices(sharedKey: ByteArray? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val devices = if (repository.savedOauthToken != null) {
                    repository.loadDevices(repository.savedOauthToken!!, sharedKey)
                } else {
                    repository.loadDevicesFromStoredCredentials()
                }

                val monitorMap = devices.associate { device ->
                    val name = device.name.ifBlank { device.id.take(8) }
                    device.id to monitorStore.ensure(device.id, name)
                }

                _state.value = _state.value.copy(
                    devices = devices,
                    monitors = monitorMap,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not load trackers")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun refreshLocation(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = repository.requestLocationOnce(device.id)
                val entry = result?.locations
                    ?.filter { it.latitude != null && it.longitude != null && it.timestamp > 0 }
                    ?.maxByOrNull { it.timestamp }
                    ?: throw IllegalStateException("No usable location report received")

                val current = monitorStore.ensure(device.id, device.name)
                val updated = if (current.armed) {
                    AlarmEngine.evaluate(
                        current,
                        LocationSample(entry.latitude!!, entry.longitude!!, entry.timestamp),
                    )
                } else {
                    current.copy(
                        lastTimestamp = entry.timestamp,
                        lastLat = entry.latitude,
                        lastLon = entry.longitude,
                        status = MonitorStatus.UNKNOWN,
                    )
                }

                monitorStore.save(updated)
                monitorStore.appendHistory(
                    device.id,
                    HistoryEntry(
                        timestamp = entry.timestamp,
                        lat = entry.latitude!!,
                        lon = entry.longitude!!,
                        distanceM = updated.lastDistanceM,
                        status = updated.status,
                    )
                )
                _state.value = _state.value.copy(
                    monitors = _state.value.monitors + (device.id to updated)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Location request failed")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun arm(device: Device) {
        val current = monitorStore.ensure(device.id, device.name)
        val lat = current.lastLat ?: return
        val lon = current.lastLon ?: return

        val armed = current.copy(
            armed = true,
            anchorLat = lat,
            anchorLon = lon,
            suspiciousCount = 0,
            status = MonitorStatus.SAFE,
            lastDistanceM = 0.0,
        )
        monitorStore.save(armed)
        _state.value = _state.value.copy(
            monitors = _state.value.monitors + (device.id to armed)
        )
    }

    fun disarm(device: Device) {
        val current = monitorStore.ensure(device.id, device.name)
        val updated = current.copy(
            armed = false,
            suspiciousCount = 0,
            status = MonitorStatus.UNKNOWN,
        )
        monitorStore.save(updated)
        _state.value = _state.value.copy(
            monitors = _state.value.monitors + (device.id to updated)
        )
    }

    fun updateConfig(
        device: Device,
        radius: Double,
        immediate: Double,
        confirmations: Int,
        intervalMinutes: Int,
        staleMinutes: Int,
    ) {
        val current = monitorStore.ensure(device.id, device.name)
        val updated = current.copy(
            safeRadiusM = radius.coerceAtLeast(10.0),
            immediateAlarmM = immediate.coerceAtLeast(radius),
            confirmations = confirmations.coerceIn(1, 5),
            intervalMinutes = intervalMinutes.coerceIn(1, 60),
            staleMinutes = staleMinutes.coerceIn(5, 24 * 60),
        )
        monitorStore.save(updated)
        _state.value = _state.value.copy(
            monitors = _state.value.monitors + (device.id to updated)
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun signOut() {
        repository.signOut()
        _state.value = AtaUiState()
    }
}
