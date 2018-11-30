package org.wordpress.android.fluxc.network.rest.wpcom.stats.time

import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.nhaarman.mockito_kotlin.KArgumentCaptor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.BaseRequest.GenericErrorType.NETWORK_ERROR
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response.Success
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.PostAndPageViewsRestClient.PostAndPageViewsResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.MONTHS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.WEEKS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.YEARS
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.API_ERROR
import org.wordpress.android.fluxc.test

@RunWith(MockitoJUnitRunner::class)
class PostAndPageViewsRestClientTest {
    @Mock private lateinit var dispatcher: Dispatcher
    @Mock private lateinit var wpComGsonRequestBuilder: WPComGsonRequestBuilder
    @Mock private lateinit var site: SiteModel
    @Mock private lateinit var requestQueue: RequestQueue
    @Mock private lateinit var accessToken: AccessToken
    @Mock private lateinit var userAgent: UserAgent
    private lateinit var urlCaptor: KArgumentCaptor<String>
    private lateinit var paramsCaptor: KArgumentCaptor<Map<String, String>>
    private lateinit var restClient: PostAndPageViewsRestClient
    private val siteId: Long = 12
    private val pageSize = 5

    @Before
    fun setUp() {
        urlCaptor = argumentCaptor()
        paramsCaptor = argumentCaptor()
        restClient = PostAndPageViewsRestClient(
                dispatcher,
                wpComGsonRequestBuilder,
                null,
                requestQueue,
                accessToken,
                userAgent
        )
    }

    @Test
    fun `returns post & page day views success response`() = test {
        testSuccessResponse(DAYS)
    }

    @Test
    fun `returns post & page day views error response`() = test {
        testErrorResponse(DAYS)
    }

    @Test
    fun `returns post & page week views success response`() = test {
        testSuccessResponse(WEEKS)
    }

    @Test
    fun `returns post & page week views error response`() = test {
        testErrorResponse(WEEKS)
    }

    @Test
    fun `returns post & page month views success response`() = test {
        testSuccessResponse(MONTHS)
    }

    @Test
    fun `returns post & page month views error response`() = test {
        testErrorResponse(MONTHS)
    }

    @Test
    fun `returns post & page year views success response`() = test {
        testSuccessResponse(YEARS)
    }

    @Test
    fun `returns post & page year views error response`() = test {
        testErrorResponse(YEARS)
    }

    private suspend fun testSuccessResponse(period: StatsGranularity) {
        val response = mock<PostAndPageViewsResponse>()
        initAllTimeResponse(response)

        val responseModel = restClient.fetchPostAndPageViews(site, period, pageSize, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(response)
        assertThat(urlCaptor.lastValue)
                .isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/stats/top-posts/")
        assertThat(paramsCaptor.lastValue).isEqualTo(
                mapOf(
                        "max" to pageSize.toString(),
                        "period" to period.toString()
                )
        )
    }

    private suspend fun testErrorResponse(period: StatsGranularity) {
        val errorMessage = "message"
        initAllTimeResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val responseModel = restClient.fetchPostAndPageViews(site, period, pageSize, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    private suspend fun initAllTimeResponse(
        data: PostAndPageViewsResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<PostAndPageViewsResponse> {
        return initResponse(PostAndPageViewsResponse::class.java, data ?: mock(), error)
    }

    private suspend fun <T> initResponse(
        kclass: Class<T>,
        data: T,
        error: WPComGsonNetworkError? = null,
        cachingEnabled: Boolean = true
    ): Response<T> {
        val response = if (error != null) Response.Error<T>(error) else Success(data)
        whenever(
                wpComGsonRequestBuilder.syncGetRequest(
                        eq(restClient),
                        urlCaptor.capture(),
                        paramsCaptor.capture(),
                        eq(kclass),
                        eq(cachingEnabled),
                        any(),
                        eq(false)
                )
        ).thenReturn(response)
        whenever(site.siteId).thenReturn(siteId)
        return response
    }
}
