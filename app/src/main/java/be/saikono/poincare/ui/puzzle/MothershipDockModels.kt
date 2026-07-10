package be.saikono.poincare.ui.puzzle

const val MOTHERSHIP_DOCK_SUBCATEGORY_ID = "mothership-dock"

data class MothershipDockRunResult(
    val docked: Boolean,
    val elapsedMs: Long,
    val shotsFired: Int,
    val enemiesDestroyed: Int,
    val friendlyHit: Boolean,
    val enemyDocked: Boolean,
    val target: String,
    val mode: String
)
