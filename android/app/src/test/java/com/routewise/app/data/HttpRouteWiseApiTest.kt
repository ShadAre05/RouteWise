package com.routewise.app.data

import com.routewise.app.core.SilentLogger
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.HttpRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.data.session.Tokens
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpRouteWiseApiTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session
    private lateinit var api: HttpRouteWiseApi

    private val tokenJson = """{"access_token":"new-access","refresh_token":"new-refresh","token_type":"bearer","expires_in":900}"""
    private fun error(code: String) = """{"error":{"code":"$code","message":"m","details":null,"request_id":"r1"}}"""

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        session = Session(InMemoryTokenStore(Tokens("old-access", "old-refresh")))
        api = HttpRouteWiseApi(server.url("/").toString(), OkHttpClient(), session, SilentLogger)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun kindOfFailure(block: suspend () -> Unit): ApiErrorKind = runBlocking {
        try {
            block()
            fail("expected ApiException")
            ApiErrorKind.UNKNOWN
        } catch (e: ApiException) {
            e.kind
        }
    }

    @Test fun sendsBearerTokenAndParsesVehicles() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"1","name":"Car","fuel_type":"diesel","consumption_l100":"5.8","tank_l":null,"is_default":true,"created_at":"x","updated_at":"x"}]"""))
        val list = api.listVehicles()
        assertEquals("Car", list.single().name)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun loginSendsNoAuthorizationHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(tokenJson))
        api.login("a@b.co", "password-1234")
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun expiredTokenIsRefreshedOnceAndRequestRepeated() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(error("unauthorized")))
        server.enqueue(MockResponse().setBody(tokenJson))
        server.enqueue(MockResponse().setBody("[]"))
        api.listVehicles()
        assertEquals(3, server.requestCount)
        server.takeRequest()
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("new-refresh", session.current?.refreshToken)
    }

    @Test fun failedRefreshEndsSession() {
        server.enqueue(MockResponse().setResponseCode(401).setBody(error("unauthorized")))
        server.enqueue(MockResponse().setResponseCode(401).setBody(error("unauthorized")))
        val kind = kindOfFailure { api.listVehicles() }
        assertEquals(ApiErrorKind.UNAUTHORIZED, kind)
        assertNull(session.current)
        assertTrue(session.expired.value)
    }

    @Test fun notFoundIsMapped() {
        server.enqueue(MockResponse().setResponseCode(404).setBody(error("not_found")))
        assertEquals(ApiErrorKind.NOT_FOUND, kindOfFailure { api.getTrip("x") })
    }

    @Test fun serverErrorIsMapped() {
        server.enqueue(MockResponse().setResponseCode(500).setBody(error("internal_error")))
        assertEquals(ApiErrorKind.SERVER, kindOfFailure { api.listTrips(10, 0) })
    }

    @Test fun noRouteCodeIsMapped() {
        server.enqueue(MockResponse().setResponseCode(422).setBody(error("no_route")))
        assertEquals(ApiErrorKind.NO_ROUTE, kindOfFailure { api.getTrip("x") })
    }

    @Test fun forbiddenIsMapped() {
        server.enqueue(MockResponse().setResponseCode(403).setBody(error("forbidden")))
        assertEquals(ApiErrorKind.FORBIDDEN, kindOfFailure { api.deleteVehicle("x") })
    }

    @Test fun unreachableServerIsNetworkError() {
        server.shutdown()
        assertEquals(ApiErrorKind.NETWORK, kindOfFailure { api.listVehicles() })
        assertFalse(session.expired.value)
    }
}
