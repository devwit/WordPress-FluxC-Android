package org.wordpress.android.fluxc.persistance.stats.time

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.StatsUtils
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.VideoPlaysRestClient.VideoPlaysResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.MONTHS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.WEEKS
import org.wordpress.android.fluxc.network.utils.StatsGranularity.YEARS
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils.BlockType.VIDEO_PLAYS
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils.StatsType.DAY
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils.StatsType.MONTH
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils.StatsType.WEEK
import org.wordpress.android.fluxc.persistence.stats.StatsSqlUtils.StatsType.YEAR
import org.wordpress.android.fluxc.persistence.stats.TimeStatsSqlUtils
import org.wordpress.android.fluxc.store.stats.time.VIDEO_PLAYS_RESPONSE
import java.util.Date
import kotlin.test.assertEquals

private val DATE = Date(0)
private const val DATE_VALUE = "2018-10-10"

@RunWith(MockitoJUnitRunner::class)
class VideoPlaysSqlUtilsTest {
    @Mock lateinit var statsSqlUtils: StatsSqlUtils
    @Mock lateinit var site: SiteModel
    @Mock lateinit var statsUtils: StatsUtils
    private lateinit var timeStatsSqlUtils: TimeStatsSqlUtils
    private val mappedTypes = mapOf(DAY to DAYS, WEEK to WEEKS, MONTH to MONTHS, YEAR to YEARS)

    @Before
    fun setUp() {
        timeStatsSqlUtils = TimeStatsSqlUtils(statsSqlUtils, statsUtils)
        whenever(statsUtils.getFormattedDate(eq(site), eq(DATE))).thenReturn(DATE_VALUE)
    }

    @Test
    fun `returns video plays from stats utils`() {
        mappedTypes.forEach { statsType, dbGranularity ->

            whenever(statsSqlUtils.select(site, VIDEO_PLAYS, statsType, VideoPlaysResponse::class.java, DATE_VALUE))
                    .thenReturn(
                            VIDEO_PLAYS_RESPONSE
                    )

            val result = timeStatsSqlUtils.selectVideoPlays(site, dbGranularity, DATE)

            assertEquals(result, VIDEO_PLAYS_RESPONSE)
        }
    }

    @Test
    fun `inserts video plays to stats utils`() {
        mappedTypes.forEach { statsType, dbGranularity ->
            timeStatsSqlUtils.insert(site, VIDEO_PLAYS_RESPONSE, dbGranularity, DATE)

            verify(statsSqlUtils).insert(site, VIDEO_PLAYS, statsType, VIDEO_PLAYS_RESPONSE, DATE_VALUE)
        }
    }
}
