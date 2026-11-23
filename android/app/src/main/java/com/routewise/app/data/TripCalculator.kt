package com.routewise.app.data

import com.routewise.app.core.Logger
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.RouteWiseApi
import com.routewise.app.data.api.TripDto
import com.routewise.app.data.api.TripRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal

/** The figures shown on a route chip (SCR-04): total and time, compared with the fastest route. */
data class VariantSummary(
    val variant: String,
    val totalEur: BigDecimal,
    val durationMin: Int,
    val distanceKm: String,
)

/** The result of a calculation: the saved trip on screen and the summaries of every route that was found. */
data class Calculation(
    val request: TripRequest,
    val selected: TripDto,
    val summaries: Map<String, VariantSummary>,
    /** Variants the server could not route (for example "no tolls" when every road is charged). */
    val unavailable: Set<String> = emptySet(),
) {
    val cheapestVariant: String?
        get() {
            val totals = summaries.values.map { it.totalEur }
            if (summaries.size < 2 || totals.distinct().size == 1) return null
            return summaries.values.minByOrNull { it.totalEur }?.variant
        }

    fun deltaEur(variant: String): BigDecimal? {
        val fastest = summaries[FASTEST] ?: return null
        val other = summaries[variant] ?: return null
        return other.totalEur - fastest.totalEur
    }

    fun deltaMinutes(variant: String): Int? {
        val fastest = summaries[FASTEST] ?: return null
        val other = summaries[variant] ?: return null
        return other.durationMin - fastest.durationMin
    }

    companion object {
        const val FASTEST = "fastest"
        const val NO_TOLLS = "no_tolls"
    }
}

/**
 * Business logic of the Plan -> Budget flow. The server saves every calculation it makes, but the user must find
 * exactly one entry in the history per calculation, so the calculator requests the three route variants
 * together, keeps the selected one and removes the other copies. Switching the route repeats this for the new
 * choice.
 */
class TripCalculator(private val api: RouteWiseApi, private val logger: Logger) {

    suspend fun calculate(base: TripRequest, keep: String = Calculation.FASTEST): Calculation {
        val outcomes = coroutineScope {
            VARIANTS.map { variant -> async { runCatching { api.createTrip(base.copy(variant = variant)) } } }.awaitAll()
        }
        val trips = VARIANTS.zip(outcomes).mapNotNull { (variant, outcome) -> outcome.getOrNull()?.let { variant to it } }.toMap()
        val fastest = trips[Calculation.FASTEST]
        if (fastest == null) {
            trips.values.forEach { discard(it) }
            throw outcomes.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IllegalStateException("no result")
        }
        val chosen = trips[keep] ?: fastest
        trips.values.filter { it !== chosen }.forEach { discard(it) }
        val unavailable = VARIANTS.zip(outcomes)
            .filter { (_, outcome) -> (outcome.exceptionOrNull() as? ApiException)?.kind == ApiErrorKind.NO_ROUTE }
            .map { it.first }.toSet()
        return Calculation(base, chosen, trips.mapValues { (_, trip) -> summaryOf(trip) }, unavailable)
    }

    /**
     * Lodging price entry (SCR-05): the same trip is calculated again with the chosen class or with the price the
     * user found, keeping the selected route. The earlier history entry is replaced, so History still shows one entry.
     */
    suspend fun withLodging(current: Calculation, lodgingClass: String, enteredEur: String?): Calculation {
        val request = current.request.copy(lodgingClass = lodgingClass, lodgingEnteredEur = enteredEur)
        val old = current.selected
        val fresh = calculate(request, keep = old.variant)
        discard(old)
        return fresh
    }

    /** Saves the chosen route as the entry of the history and removes the previous one. */
    suspend fun select(current: Calculation, variant: String): Calculation {
        if (variant == current.selected.variant || variant !in current.summaries) return current
        val chosen = api.createTrip(current.request.copy(variant = variant))
        discard(current.selected)
        return current.copy(selected = chosen, summaries = current.summaries + (variant to summaryOf(chosen)))
    }

    private suspend fun discard(trip: TripDto) {
        try {
            api.deleteTrip(trip.id)
        } catch (e: Exception) {
            logger.warn("Could not remove an alternative route from the history")
        }
    }

    private fun summaryOf(trip: TripDto) =
        VariantSummary(trip.variant, BigDecimal(trip.totalEur), trip.durationMin, trip.distanceKm)

    companion object {
        val VARIANTS = listOf("fastest", "shortest", "no_tolls")
    }
}
