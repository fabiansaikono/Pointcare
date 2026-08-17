package be.saikono.poincare.ui

sealed class Screen(val route: String) {
    object SpaceMap : Screen("space_map")
    object LearningGalaxy : Screen("learning_galaxy")
    object ProfileSelection : Screen("profile_selection")
    object CreateProfile : Screen("create_profile")
    object ParentDashboard : Screen("parent_dashboard")
    object Tasks : Screen("tasks")
    object Math : Screen("math")
    object Reading : Screen("reading")
    object StoryLibrary : Screen("story_library")
    object StoryBook : Screen("story_book/{bookId}") {
        fun createRoute(bookId: String) = "story_book/$bookId"
    }
    object StoryReader : Screen("story_reader/{storyId}") {
        fun createRoute(storyId: String) = "story_reader/$storyId"
    }
    object Writing : Screen("writing")
    object Puzzle : Screen("puzzle")
    object OrbitBuilder : Screen("orbit_builder")
    object LessonHub : Screen("lesson_hub/{domain}/{subcategoryId}") {
        fun createRoute(domain: String, subcategoryId: String) = "lesson_hub/$domain/$subcategoryId"
    }
    object LessonIntro : Screen("lesson_intro/{lessonId}") {
        fun createRoute(lessonId: String) = "lesson_intro/$lessonId"
    }
    object LessonPlayer : Screen("lesson_player/{lessonId}/{stageId}") {
        const val WHOLE_LESSON = "all"
        fun createRoute(lessonId: String, stageId: String? = null) =
            "lesson_player/$lessonId/${stageId ?: WHOLE_LESSON}"
    }
    object SubCategorySelection : Screen("subcategory_selection/{domain}") {
        fun createRoute(domain: String) = "subcategory_selection/$domain"
    }
}

