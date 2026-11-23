package com.routewise.app

import android.content.Context
import com.routewise.app.core.AndroidLogger
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Logger
import com.routewise.app.core.Notifier
import com.routewise.app.data.Calculation
import kotlinx.coroutines.flow.MutableStateFlow
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.TripRepository
import com.routewise.app.data.VehicleRepository
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.api.HttpRouteWiseApi
import com.routewise.app.data.api.RouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.SecureTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.data.session.TokenStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection: one place that decides which implementation the app uses. Tests build their own
 * container with a fake API; the app builds this one from the build configuration (demo mode or real server).
 */
class AppContainer(
    val logger: Logger,
    val session: Session,
    val api: RouteWiseApi,
) {
    val errors = ErrorHandler(logger)
    val notifier = Notifier()

    /** The calculation shown on the Budget screen; set by the Plan screen. */
    val currentCalculation = MutableStateFlow<Calculation?>(null)

    val auth = AuthRepository(api, session)
    val vehicles = VehicleRepository(api)
    val trips = TripRepository(api)
    val calculator = TripCalculator(api, logger)

    companion object {
        fun create(context: Context): AppContainer {
            val logger = AndroidLogger()
            val store: TokenStore = if (BuildConfig.DEMO_MODE) InMemoryTokenStore() else SecureTokenStore(context)
            val session = Session(store)
            val api: RouteWiseApi = if (BuildConfig.DEMO_MODE) {
                FakeRouteWiseApi(session)
            } else {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                HttpRouteWiseApi(BuildConfig.API_BASE_URL, client, session, logger)
            }
            return AppContainer(logger, session, api)
        }
    }
}
