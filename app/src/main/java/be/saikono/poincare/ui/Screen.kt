package be.saikono.poincare.ui

sealed class Screen(val route: String) {
    object SpaceMap : Screen("space_map")
    object ProfileSelection : Screen("profile_selection")
    object CreateProfile : Screen("create_profile")
    object ParentDashboard : Screen("parent_dashboard")
    object Math : Screen("math")
    object Reading : Screen("reading")
    object Writing : Screen("writing")
    object Puzzle : Screen("puzzle")
    object OrbitBuilder : Screen("orbit_builder")
    object SubCategorySelection : Screen("subcategory_selection/{domain}") {
        fun createRoute(domain: String) = "subcategory_selection/$domain"
    }
}

