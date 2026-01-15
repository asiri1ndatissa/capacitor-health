package app.capgo.plugin.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import java.time.Instant
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class ReadAuthorizationTypes(
    val includeWorkouts: Boolean,
    val includeSleep: Boolean,
    val includeHydration: Boolean
)

@CapacitorPlugin(name = "Health")
class HealthPlugin : Plugin() {
    private val pluginVersion = "7.2.14"
    private val manager = HealthManager()
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val permissionContract = PermissionController.createRequestPermissionResultContract()

    // Store pending request data for callback
    private var pendingIncludeWorkouts: Boolean = false
    private var pendingIncludeSleep: Boolean = false
    private var pendingIncludeHydration: Boolean = false

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        pluginScope.cancel()
    }

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        call.resolve(availabilityPayload(status))
    }

    @PluginMethod
    fun requestAuthorization(call: PluginCall) {
        val readAuth = try {
            parseReadAuthorizationTypes(call, "read")
        } catch (e: IllegalArgumentException) {
            call.reject(e.message, null, e)
            return
        }

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            val permissions = manager.permissionsFor(
                readAuth.includeWorkouts,
                readAuth.includeSleep,
                readAuth.includeHydration
            )

            if (permissions.isEmpty()) {
                val status = manager.authorizationStatus(
                    client,
                    readAuth.includeWorkouts,
                    readAuth.includeSleep,
                    readAuth.includeHydration
                )
                call.resolve(status)
                return@launch
            }

            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) {
                val status = manager.authorizationStatus(
                    client,
                    readAuth.includeWorkouts,
                    readAuth.includeSleep,
                    readAuth.includeHydration
                )
                call.resolve(status)
                return@launch
            }

            // Store types for callback
            pendingIncludeWorkouts = readAuth.includeWorkouts
            pendingIncludeSleep = readAuth.includeSleep
            pendingIncludeHydration = readAuth.includeHydration

            // Create intent using the Health Connect permission contract
            val intent = permissionContract.createIntent(context, permissions)

            try {
                startActivityForResult(call, intent, "handlePermissionResult")
            } catch (e: Exception) {
                call.reject("Failed to launch Health Connect permission request.", null, e)
            }
        }
    }

    @ActivityCallback
    private fun handlePermissionResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) {
            return
        }

        val includeWorkouts = pendingIncludeWorkouts
        val includeSleep = pendingIncludeSleep
        val includeHydration = pendingIncludeHydration
        pendingIncludeWorkouts = false
        pendingIncludeSleep = false
        pendingIncludeHydration = false

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            val status = manager.authorizationStatus(client, includeWorkouts, includeSleep, includeHydration)
            call.resolve(status)
        }
    }

    @PluginMethod
    fun checkAuthorization(call: PluginCall) {
        val readAuth = try {
            parseReadAuthorizationTypes(call, "read")
        } catch (e: IllegalArgumentException) {
            call.reject(e.message, null, e)
            return
        }

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            val status = manager.authorizationStatus(
                client,
                readAuth.includeWorkouts,
                readAuth.includeSleep,
                readAuth.includeHydration
            )
            call.resolve(status)
        }
    }


    private fun parseReadAuthorizationTypes(call: PluginCall, key: String): ReadAuthorizationTypes {
        val array = call.getArray(key) ?: JSArray()
        var includeWorkouts = false
        var includeSleep = false
        var includeHydration = false
        for (i in 0 until array.length()) {
            val identifier = array.optString(i, null) ?: continue
            when (identifier) {
                "workouts" -> includeWorkouts = true
                "sleep" -> includeSleep = true
                "hydration" -> includeHydration = true
                else -> throw IllegalArgumentException("Unsupported data type: $identifier")
            }
        }
        return ReadAuthorizationTypes(includeWorkouts, includeSleep, includeHydration)
    }

    private fun getClientOrReject(call: PluginCall): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            call.reject(availabilityReason(status))
            return null
        }
        return HealthConnectClient.getOrCreate(context)
    }

    private fun availabilityPayload(status: Int): JSObject {
        val payload = JSObject()
        payload.put("platform", "android")
        payload.put("available", status == HealthConnectClient.SDK_AVAILABLE)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            payload.put("reason", availabilityReason(status))
        }
        return payload
    }

    private fun availabilityReason(status: Int): String {
        return when (status) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect needs an update."
            HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect is unavailable on this device."
            else -> "Health Connect availability unknown."
        }
    }

    @PluginMethod
    fun getPluginVersion(call: PluginCall) {
        try {
            val ret = JSObject()
            ret.put("version", pluginVersion)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Could not get plugin version", e)
        }
    }

    @PluginMethod
    fun openHealthConnectSettings(call: PluginCall) {
        try {
            val intent = Intent(HEALTH_CONNECT_SETTINGS_ACTION)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to open Health Connect settings", null, e)
        }
    }

    @PluginMethod
    fun showPrivacyPolicy(call: PluginCall) {
        try {
            val intent = Intent(context, PermissionsRationaleActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to show privacy policy", null, e)
        }
    }

    @PluginMethod
    fun queryWorkouts(call: PluginCall) {
        val workoutType = call.getString("workoutType")
        val limit = (call.getInt("limit") ?: DEFAULT_LIMIT).coerceAtLeast(0)
        val ascending = call.getBoolean("ascending") ?: false

        val startInstant = try {
            manager.parseInstant(call.getString("startDate"), Instant.now().minus(DEFAULT_PAST_DURATION))
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        val endInstant = try {
            manager.parseInstant(call.getString("endDate"), Instant.now())
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        if (endInstant.isBefore(startInstant)) {
            call.reject("endDate must be greater than or equal to startDate")
            return
        }

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            try {
                val workouts = manager.queryWorkouts(client, workoutType, startInstant, endInstant, limit, ascending)
                val result = JSObject().apply { put("workouts", workouts) }
                call.resolve(result)
            } catch (e: Exception) {
                call.reject(e.message ?: "Failed to query workouts.", null, e)
            }
        }
    }

    @PluginMethod
    fun querySleep(call: PluginCall) {
        val limit = (call.getInt("limit") ?: DEFAULT_LIMIT).coerceAtLeast(0)
        val ascending = call.getBoolean("ascending") ?: false

        val startInstant = try {
            manager.parseInstant(call.getString("startDate"), Instant.now().minus(DEFAULT_PAST_DURATION))
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        val endInstant = try {
            manager.parseInstant(call.getString("endDate"), Instant.now())
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        if (endInstant.isBefore(startInstant)) {
            call.reject("endDate must be greater than or equal to startDate")
            return
        }

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            try {
                val sleepSessions = manager.querySleep(client, startInstant, endInstant, limit, ascending)
                val result = JSObject().apply { put("sleepSessions", sleepSessions) }
                call.resolve(result)
            } catch (e: Exception) {
                call.reject(e.message ?: "Failed to query sleep sessions.", null, e)
            }
        }
    }

    @PluginMethod
    fun queryHydration(call: PluginCall) {
        val limit = (call.getInt("limit") ?: DEFAULT_LIMIT).coerceAtLeast(0)
        val ascending = call.getBoolean("ascending") ?: false

        val startInstant = try {
            manager.parseInstant(call.getString("startDate"), Instant.now().minus(DEFAULT_PAST_DURATION))
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        val endInstant = try {
            manager.parseInstant(call.getString("endDate"), Instant.now())
        } catch (e: DateTimeParseException) {
            call.reject(e.message, null, e)
            return
        }

        if (endInstant.isBefore(startInstant)) {
            call.reject("endDate must be greater than or equal to startDate")
            return
        }

        pluginScope.launch {
            val client = getClientOrReject(call) ?: return@launch
            try {
                val hydrationRecords = manager.queryHydration(client, startInstant, endInstant, limit, ascending)
                val result = JSObject().apply { put("hydrationRecords", hydrationRecords) }
                call.resolve(result)
            } catch (e: Exception) {
                call.reject(e.message ?: "Failed to query hydration records.", null, e)
            }
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 100
        private val DEFAULT_PAST_DURATION: Duration = Duration.ofDays(1)
        private const val HEALTH_CONNECT_SETTINGS_ACTION = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
    }
}
