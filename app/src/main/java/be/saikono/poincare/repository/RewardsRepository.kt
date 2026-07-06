package be.saikono.poincare.repository

import be.saikono.poincare.data.local.ParentRewardEntity
import be.saikono.poincare.data.local.RewardDao
import be.saikono.poincare.data.local.StampCardEntity
import be.saikono.poincare.model.ParentReward
import be.saikono.poincare.model.StampCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface RewardsRepository {
    fun getRewardsForChild(childId: String): Flow<List<ParentReward>>
    suspend fun saveReward(reward: ParentReward)
    fun getStampCardForChild(childId: String): Flow<StampCard?>
    suspend fun saveStampCard(stampCard: StampCard)
}

class LocalRewardsRepository(private val rewardDao: RewardDao) : RewardsRepository {
    override fun getRewardsForChild(childId: String): Flow<List<ParentReward>> =
        rewardDao.getRewardsForChild(childId).map { entities -> entities.map { it.toModel() } }

    override suspend fun saveReward(reward: ParentReward) =
        rewardDao.insertReward(reward.toEntity())

    override fun getStampCardForChild(childId: String): Flow<StampCard?> =
        rewardDao.getStampCardForChild(childId).map { it?.toModel() }

    override suspend fun saveStampCard(stampCard: StampCard) =
        rewardDao.insertStampCard(stampCard.toEntity())
}

private fun ParentRewardEntity.toModel() = ParentReward(
    id = id,
    childProfileId = childProfileId,
    tier = tier,
    label = try {
        Json.decodeFromString<Map<String, String>>(labelsJson)
    } catch (e: Exception) {
        emptyMap()
    },
    stampsRequired = stampsRequired,
    currentStamps = currentStamps,
    isClaimed = isClaimed,
    requiresParentApproval = requiresParentApproval
)

private fun ParentReward.toEntity() = ParentRewardEntity(
    id = id,
    childProfileId = childProfileId,
    tier = tier,
    labelsJson = Json.encodeToString(label),
    stampsRequired = stampsRequired,
    currentStamps = currentStamps,
    isClaimed = isClaimed,
    requiresParentApproval = requiresParentApproval
)

private fun StampCardEntity.toModel() = StampCard(
    id = id,
    childProfileId = childProfileId,
    totalStamps = totalStamps,
    lastUpdated = lastUpdated
)

private fun StampCard.toEntity() = StampCardEntity(
    id = id,
    childProfileId = childProfileId,
    totalStamps = totalStamps,
    lastUpdated = lastUpdated
)
