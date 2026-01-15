package app.capgo.plugin.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.collections.buildSet

class HealthManager {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun permissionsFor(includeWorkouts: Boolean = false, includeSleep: Boolean = false, includeHydration: Boolean = false): Set<String> = buildSet {
        // Include workout read permission if explicitly requested
        if (includeWorkouts) {
            add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
        }
        // Include sleep read permission if explicitly requested
        if (includeSleep) {
            add(HealthPermission.getReadPermission(SleepSessionRecord::class))
        }
        // Include hydration read permission if explicitly requested
        if (includeHydration) {
            add(HealthPermission.getReadPermission(HydrationRecord::class))
        }
    }

    suspend fun authorizationStatus(
        client: HealthConnectClient,
        includeWorkouts: Boolean = false,
        includeSleep: Boolean = false,
        includeHydration: Boolean = false
    ): JSObject {
        val granted = client.permissionController.getGrantedPermissions()

        val readAuthorized = JSArray()
        val readDenied = JSArray()

        // Check workout permission if requested
        if (includeWorkouts) {
            val workoutPermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            if (granted.contains(workoutPermission)) {
                readAuthorized.put("workouts")
            } else {
                readDenied.put("workouts")
            }
        }

        // Check sleep permission if requested
        if (includeSleep) {
            val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
            if (granted.contains(sleepPermission)) {
                readAuthorized.put("sleep")
            } else {
                readDenied.put("sleep")
            }
        }

        // Check hydration permission if requested
        if (includeHydration) {
            val hydrationPermission = HealthPermission.getReadPermission(HydrationRecord::class)
            if (granted.contains(hydrationPermission)) {
                readAuthorized.put("hydration")
            } else {
                readDenied.put("hydration")
            }
        }

        val writeAuthorized = JSArray()
        val writeDenied = JSArray()

        return JSObject().apply {
            put("readAuthorized", readAuthorized)
            put("readDenied", readDenied)
            put("writeAuthorized", writeAuthorized)
            put("writeDenied", writeDenied)
        }
    }


    fun parseInstant(value: String?, defaultInstant: Instant): Instant {
        if (value.isNullOrBlank()) {
            return defaultInstant
        }
        return Instant.parse(value)
    }


    private fun zoneOffset(instant: Instant): ZoneOffset? {
        return ZoneId.systemDefault().rules.getOffset(instant)
    }

    suspend fun queryWorkouts(
        client: HealthConnectClient,
        workoutType: String?,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        ascending: Boolean
    ): JSArray {
        val workouts = mutableListOf<Pair<Instant, JSObject>>()

        var pageToken: String? = null
        val pageSize = if (limit > 0) min(limit, MAX_PAGE_SIZE) else DEFAULT_PAGE_SIZE
        var fetched = 0

        val exerciseTypeFilter = WorkoutType.fromString(workoutType)

        do {
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageSize = pageSize,
                pageToken = pageToken
            )
            val response = client.readRecords(request)

            response.records.forEach { record ->
                val session = record as ExerciseSessionRecord

                // Filter by exercise type if specified
                if (exerciseTypeFilter != null && session.exerciseType != exerciseTypeFilter) {
                    return@forEach
                }

                val payload = createWorkoutPayload(session)
                workouts.add(session.startTime to payload)
            }

            fetched += response.records.size
            pageToken = response.pageToken
        } while (pageToken != null && (limit <= 0 || fetched < limit))

        val sorted = workouts.sortedBy { it.first }
        val ordered = if (ascending) sorted else sorted.asReversed()
        val limited = if (limit > 0) ordered.take(limit) else ordered

        val array = JSArray()
        limited.forEach { array.put(it.second) }
        return array
    }

    private fun createWorkoutPayload(session: ExerciseSessionRecord): JSObject {
        val payload = JSObject()

        // Workout type
        payload.put("workoutType", WorkoutType.toWorkoutTypeString(session.exerciseType))

        // Duration in seconds
        val durationSeconds = Duration.between(session.startTime, session.endTime).seconds.toInt()
        payload.put("duration", durationSeconds)

        // Start and end dates
        payload.put("startDate", formatter.format(session.startTime))
        payload.put("endDate", formatter.format(session.endTime))

        // Source information
        val dataOrigin = session.metadata.dataOrigin
        payload.put("sourceId", dataOrigin.packageName)
        payload.put("sourceName", dataOrigin.packageName)
        session.metadata.device?.let { device ->
            val manufacturer = device.manufacturer?.takeIf { it.isNotBlank() }
            val model = device.model?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(manufacturer, model).joinToString(" ").trim()
            if (label.isNotEmpty()) {
                payload.put("sourceName", label)
            }
        }

        return payload
    }

    suspend fun querySleep(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        ascending: Boolean
    ): JSArray {
        val sleepSessions = mutableListOf<Pair<Instant, JSObject>>()
        
        var pageToken: String? = null
        val pageSize = if (limit > 0) min(limit, MAX_PAGE_SIZE) else DEFAULT_PAGE_SIZE
        var fetched = 0
        
        do {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageSize = pageSize,
                pageToken = pageToken
            )
            val response = client.readRecords(request)
            
            response.records.forEach { record ->
                val session = record as SleepSessionRecord
                val payload = createSleepPayload(session)
                sleepSessions.add(session.startTime to payload)
            }
            
            fetched += response.records.size
            pageToken = response.pageToken
        } while (pageToken != null && (limit <= 0 || fetched < limit))
        
        val sorted = sleepSessions.sortedBy { it.first }
        val ordered = if (ascending) sorted else sorted.asReversed()
        val limited = if (limit > 0) ordered.take(limit) else ordered
        
        val array = JSArray()
        limited.forEach { array.put(it.second) }
        return array
    }

    private fun createSleepPayload(session: SleepSessionRecord): JSObject {
        val payload = JSObject()

        // Title if available
        session.title?.let { title ->
            if (title.isNotBlank()) {
                payload.put("title", title)
            }
        }

        // Duration in seconds
        val durationSeconds = Duration.between(session.startTime, session.endTime).seconds.toInt()
        payload.put("duration", durationSeconds)

        // Start and end dates
        payload.put("startDate", formatter.format(session.startTime))
        payload.put("endDate", formatter.format(session.endTime))

        // Sleep stages if available
        if (session.stages.isNotEmpty()) {
            val stagesArray = JSArray()
            session.stages.forEach { stage ->
                val stageObject = JSObject()
                stageObject.put("stage", sleepStageToString(stage.stage))
                stageObject.put("startDate", formatter.format(stage.startTime))
                stageObject.put("endDate", formatter.format(stage.endTime))
                stagesArray.put(stageObject)
            }
            payload.put("stages", stagesArray)
        }

        // Source information
        val dataOrigin = session.metadata.dataOrigin
        payload.put("sourceId", dataOrigin.packageName)
        payload.put("sourceName", dataOrigin.packageName)
        session.metadata.device?.let { device ->
            val manufacturer = device.manufacturer?.takeIf { it.isNotBlank() }
            val model = device.model?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(manufacturer, model).joinToString(" ").trim()
            if (label.isNotEmpty()) {
                payload.put("sourceName", label)
            }
        }

        return payload
    }

    private fun sleepStageToString(stage: Int): String {
        return when (stage) {
            SleepSessionRecord.STAGE_TYPE_UNKNOWN -> "unknown"
            SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "outOfBed"
            SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
            SleepSessionRecord.STAGE_TYPE_REM -> "rem"
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "awakeInBed"
            else -> "unknown"
        }
    }

    suspend fun queryHydration(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant,
        limit: Int,
        ascending: Boolean
    ): JSArray {
        val hydrationRecords = mutableListOf<Pair<Instant, JSObject>>()

        var pageToken: String? = null
        val pageSize = if (limit > 0) min(limit, MAX_PAGE_SIZE) else DEFAULT_PAGE_SIZE
        var fetched = 0

        do {
            val request = ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageSize = pageSize,
                pageToken = pageToken
            )
            val response = client.readRecords(request)

            response.records.forEach { record ->
                val hydration = record as HydrationRecord
                val payload = createHydrationPayload(hydration)
                hydrationRecords.add(hydration.startTime to payload)
            }

            fetched += response.records.size
            pageToken = response.pageToken
        } while (pageToken != null && (limit <= 0 || fetched < limit))

        val sorted = hydrationRecords.sortedBy { it.first }
        val ordered = if (ascending) sorted else sorted.asReversed()
        val limited = if (limit > 0) ordered.take(limit) else ordered

        val array = JSArray()
        limited.forEach { array.put(it.second) }
        return array
    }

    private fun createHydrationPayload(hydration: HydrationRecord): JSObject {
        val payload = JSObject()

        // Volume in liters
        val volumeLiters = hydration.volume.inLiters
        payload.put("volume", volumeLiters)

        // Start and end dates
        payload.put("startDate", formatter.format(hydration.startTime))
        payload.put("endDate", formatter.format(hydration.endTime))

        // Source information
        val dataOrigin = hydration.metadata.dataOrigin
        payload.put("sourceId", dataOrigin.packageName)
        payload.put("sourceName", dataOrigin.packageName)
        hydration.metadata.device?.let { device ->
            val manufacturer = device.manufacturer?.takeIf { it.isNotBlank() }
            val model = device.model?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(manufacturer, model).joinToString(" ").trim()
            if (label.isNotEmpty()) {
                payload.put("sourceName", label)
            }
        }

        // Metadata if available
        if (hydration.metadata.clientRecordId != null || hydration.metadata.clientRecordVersion > 0) {
            val metadataObj = JSObject()
            hydration.metadata.clientRecordId?.let { metadataObj.put("clientRecordId", it) }
            if (hydration.metadata.clientRecordVersion > 0) {
                metadataObj.put("clientRecordVersion", hydration.metadata.clientRecordVersion)
            }
            if (metadataObj.length() > 0) {
                payload.put("metadata", metadataObj)
            }
        }

        return payload
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 500
    }
}
