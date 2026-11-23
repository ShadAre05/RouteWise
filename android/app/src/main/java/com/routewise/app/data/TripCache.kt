package com.routewise.app.data

import com.routewise.app.data.api.ApiJson
import com.routewise.app.data.api.TripDto
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Keeps the last loaded history so that it can be shown without a connection (UX SCR-06, offline banner). */
interface TripCache {
    fun load(): List<TripDto>
    fun save(trips: List<TripDto>)
    fun clear()
}

class InMemoryTripCache : TripCache {
    private var trips: List<TripDto> = emptyList()
    override fun load() = trips
    override fun save(trips: List<TripDto>) { this.trips = trips }
    override fun clear() { trips = emptyList() }
}

/** Stores the list as JSON in the private files directory of the app; it is removed on sign-out. */
class FileTripCache(private val file: File) : TripCache {
    private val serializer = ListSerializer(TripDto.serializer())

    override fun load(): List<TripDto> =
        try {
            if (file.exists()) ApiJson.decodeFromString(serializer, file.readText()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    override fun save(trips: List<TripDto>) {
        try {
            file.writeText(ApiJson.encodeToString(serializer, trips))
        } catch (e: Exception) {
            // The cache is an extra; failing to write it must never break the screen.
        }
    }

    override fun clear() {
        file.delete()
    }
}
