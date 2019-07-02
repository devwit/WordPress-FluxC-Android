package org.wordpress.android.fluxc.persistence.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.BlockType
import org.wordpress.android.fluxc.persistence.StatsSqlUtils.StatsType

@Dao
interface StatsDao {
    @Insert(onConflict = REPLACE)
    fun insertOrReplace(statsBlock: StatsBlock)

    @Query(
            """
        DELETE from StatsBlock
        WHERE localSiteId == :localSiteId
        AND blockType == :blockType
        AND statsType == :statsType
        AND (:date IS NULL OR date == :date)
        AND (:postId IS NULL OR postId == :postId)
        """
    )
    fun delete(localSiteId: Int, blockType: BlockType, statsType: StatsType, date: String? = null, postId: Long? = null)

    @Query(
            """
        SELECT json FROM StatsBlock
        WHERE localSiteId == :localSiteId
        AND blockType == :blockType
        AND statsType == :statsType
        AND (:date IS NULL OR date == :date)
        AND (:postId IS NULL OR postId == :postId)
        """
    )
    fun liveSelectAll(
        localSiteId: Int,
        blockType: BlockType,
        statsType: StatsType,
        date: String? = null,
        postId: Long? = null
    ): LiveData<List<String>>

    @Query(
            """
        SELECT json FROM StatsBlock
        WHERE localSiteId == :localSiteId
        AND blockType == :blockType
        AND statsType == :statsType
        AND (:date IS NULL OR date == :date)
        AND (:postId IS NULL OR postId == :postId)
        LIMIT 1
        """
    )
    fun liveSelect(
        localSiteId: Int,
        blockType: BlockType,
        statsType: StatsType,
        date: String? = null,
        postId: Long? = null
    ): LiveData<String>

    @Query(
            """
        SELECT json FROM StatsBlock
        WHERE localSiteId == :localSiteId
        AND blockType == :blockType
        AND statsType == :statsType
        AND (:date IS NULL OR date == :date)
        AND (:postId IS NULL OR postId == :postId)
        LIMIT 1
        """
    )
    fun select(
        localSiteId: Int,
        blockType: BlockType,
        statsType: StatsType,
        date: String? = null,
        postId: Long? = null
    ): String?

    @Query(
            """
        SELECT json FROM StatsBlock
        WHERE localSiteId == :localSiteId
        AND blockType == :blockType
        AND statsType == :statsType
        AND (:date IS NULL OR date == :date)
        AND (:postId IS NULL OR postId == :postId)
        """
    )
    fun selectAll(
        localSiteId: Int,
        blockType: BlockType,
        statsType: StatsType,
        date: String? = null,
        postId: Long? = null
    ): List<String>

}