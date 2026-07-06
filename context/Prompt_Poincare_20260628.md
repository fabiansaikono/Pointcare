You are Gemini inside Android Studio. Act as a senior Android architect, Kotlin developer, Jetpack Compose expert, educational app designer, UX designer, animation designer, and curriculum-aware learning product designer for Belgian primary education.

I am building an Android app called “Poincaré”.

Important: do not try to build the full app in one response. Work in small, safe, buildable steps. For every step:

1. Inspect the existing project structure before changing code.
2. Explain briefly what you will add or change.
3. Create or modify only the necessary files.
4. Keep the app compiling after every step.
5. Avoid placeholders that break the build.
6. Use clean Kotlin and Jetpack Compose.
7. Prefer small reusable components.
8. Do not hardcode exercises in UI screens. Learning content should come from JSON assets.
9. When you create code, mention which files were created or modified.
10. After each step, suggest the next logical step.

APP CONCEPT
Poincaré is an Android learning app for children. It combines:

* mathematics;
* mathematical puzzles;
* learning to read;
* learning to write.

The first concrete child user will be my son Henri, but the app must not be hardcoded for Henri. The app must support multiple child profiles. I will create Henri’s profile myself inside the app.

Henri’s starting levels are:

* reading: first grade primary school;
* writing: first grade primary school;
* mathematics: fourth grade primary school;
* puzzles: adaptive, starting around fourth-grade maths level but able to scale faster.

The app must first support Dutch/Flemish, then Belgian French, then English.

The app must be offline-first, but after every exercise it should try to save/sync progress online. If there is no internet, progress must be saved locally and synced later.

DESIGN DIRECTION
The visual style should be space-themed, calm, premium, slightly playful, but not too childish.

Use concepts like:

* missions;
* planets;
* orbit;
* rocket;
* cockpit;
* board computer;
* space codes;
* star map;
* cargo;
* unlocks;
* badges.

Avoid:

* babyish mascots;
* chaotic screens;
* loud animations;
* excessive confetti;
* too many bright colors;
* distracting visual clutter.

The app should feel smart and challenging, especially because it must support gifted children.

CORE FEATURES
The MVP must eventually include:

1. Onboarding.
2. Parent PIN.
3. Multiple child profiles.
4. Profile selection.
5. Space map home screen.
6. Reading module.
7. Writing module.
8. Maths module.
9. Puzzle module.
10. Reward/stamp-card module.
11. Parent dashboard.
12. Local progress tracking.
13. Online progress sync after every exercise.
14. Animation settings.
15. Language support.
16. JSON-driven content.

TECH STACK
Use:

* Kotlin;
* Jetpack Compose;
* Material 3;
* single-activity architecture;
* ViewModel;
* StateFlow;
* unidirectional data flow;
* repository pattern;
* Room for local progress;
* DataStore for settings;
* JSON files in assets for learning content;
* Android TextToSpeech or prepared audio hooks;
* WorkManager for background sync;
* Compose animations;
* Canvas where useful for letter tracing;
* offline-first architecture.

PROJECT STRUCTURE
Create or evolve the project toward this package structure:

ui/
onboarding/
profile/
home/
spaceMap/
math/
reading/
writing/
puzzle/
rewards/
parent/
settings/
components/
animations/

domain/
exercise/
puzzle/
reading/
writing/
adaptive/
rewards/
language/
sync/

data/
content/
progress/
profile/
rewards/
settings/
sync/

model/

CORE DOMAIN MODEL
The app must support multiple child profiles.

Create models similar to:

ChildProfile:

* id;
* name;
* age;
* avatarId;
* rocketColorId;
* appLanguage;
* exerciseLanguage;
* supportLanguage;
* speechLanguage;
* readingLevel;
* writingLevel;
* mathLevel;
* puzzleLevel;
* giftedModeEnabled;
* createdAt;
* updatedAt.

ProgressEvent:

* id;
* childProfileId;
* exerciseId;
* domain;
* skill;
* timestamp;
* answerCorrect;
* responseTimeMs;
* hintsUsed;
* languageSwitches;
* translationsUsed;
* retryCount;
* rewardStampsEarned;
* synced.

ParentReward:

* id;
* childProfileId;
* tier;
* requiredStamps;
* labelNl;
* labelFr;
* labelEn;
* requiresParentApproval;
* isActive.

AnimationSettings:

* animationsEnabled;
* reducedMotionEnabled;
* soundEffectsEnabled;
* hapticFeedbackEnabled;
* unlockAnimationsEnabled;
* maxAnimationIntensity.

LANGUAGE FEATURES
The app must support:

* app language;
* exercise language;
* support language;
* speech language;
* switching support language during exercises;
* tap-to-translate for language-bound exercises;
* parent control to disable translation;
* parent control to force a target language.

For maths exercises, switching language during an exercise is allowed.

For reading exercises, translation is only a support layer. It must not replace the reading goal.

Example:
Target language: French.
Word: chat.
The child hears the French pronunciation.
The child reads the French word.
On tap, the app may show the Dutch meaning “kat”.
But the exercise remains a French reading exercise.

EDUCATIONAL CONTENT
Use curriculum-aware design inspired by Belgian primary education.

Reading Dutch/Flemish, first-grade level:

* lowercase letters;
* uppercase/print letters;
* letter-sound correspondence;
* phonemes;
* graphemes;
* short vowels;
* long vowels;
* digraphs;
* schwa/domme e/doffe e;
* building simple words;
* reading simple sentences;
* simple comprehension.

Writing Dutch/Flemish, first-grade level:

* letter recognition;
* print letters;
* tracing letters;
* start point;
* writing direction;
* stroke order;
* copying;
* completing words;
* building short words;
* optional finger/stylus tracing.

Maths, fourth-grade level:

* number sense;
* mental arithmetic;
* addition and subtraction with larger numbers;
* multiplication;
* division;
* multiplication tables;
* division tables;
* mixed tables;
* open exercises;
* place value;
* building numbers from ten-thousands, thousands, hundreds, tens and units;
* fractions;
* simple decimals if appropriate;
* measurement;
* geometry;
* patterns;
* logical reasoning;
* simple word problems;
* relation between multiplication and division.

Puzzles:

* sudoku;
* math crossword;
* number wall;
* magic squares;
* balance puzzles;
* operator puzzles;
* sequences;
* Calcudoku/KenKen-like puzzles;
* code breaker puzzles;
* drag-and-drop number puzzles.

CONTENT SOURCE
Learning content must be loaded from JSON files in assets.

Do not hardcode exercises directly in Compose screens.

Create JSON schemas or Kotlin serializable data models for:

* exercise id;
* locale;
* supported locales;
* domain;
* skill;
* section;
* level;
* schoolLevel;
* difficulty;
* inputMode;
* prompt;
* speech;
* translation;
* visual assets;
* audio assets;
* animation metadata;
* answer model;
* hints;
* feedback;
* curriculum references;
* tags;
* estimated time;
* mastery weight;
* reward metadata;
* adaptive metadata.

Sections:

* practice;
* exercises;
* puzzles;
* tests.

Input modes:

* custom_number_pad;
* fraction_input;
* decimal_input;
* drag_drop_digits;
* drag_drop_letters;
* tap_choice;
* listen_and_select;
* build_word;
* trace_letter;
* match_pairs;
* tap_to_translate;
* math_crossword;
* sudoku_grid;
* number_wall;
* balance_puzzle;
* operator_puzzle.

INPUT RULES
Never use the standard Android keyboard for number exercises.

For number answers, create a custom number pad with:

* digits 0 to 9;
* delete;
* confirm;
* optional comma;
* optional minus;
* optional fraction mode;
* large clear buttons;
* subtle animation;
* optional haptic feedback.

For puzzles, use drag-and-drop.

For writing, use tracing with finger or stylus where possible.

For letters and words, use tiles, tapping, dragging, listening and building.

ANIMATIONS
Animations are an essential part of the UX, but they must be calm and functional.

General animation rules:

* meaningful;
* short;
* not distracting;
* accessible;
* configurable;
* reduced-motion support;
* no excessive confetti;
* no loud chaotic effects.

Create reusable animation components such as:

* MissionStartAnimation;
* CorrectAnswerAnimation;
* IncorrectAnswerSignalAnimation;
* HintPanelAnimation;
* DragLiftAnimation;
* DropSnapAnimation;
* LetterTraceRocketAnimation;
* LetterSoundPulseAnimation;
* WordCompleteAnimation;
* PuzzleSolvedAnimation;
* TestScanAnimation;
* StampCardAnimation;
* UnlockRevealAnimation;
* SpaceMapOrbitAnimation;
* ReducedMotionAnimationSet.

Animation behavior:

* correct answer: subtle star particles, soft orbit pulse;
* wrong answer: soft signal noise, gentle shake;
* hint: board computer panel slides in;
* drag item: tile lifts slightly;
* correct drop: tile snaps into place;
* wrong drop: tile gently returns;
* letter tracing: rocket trail shows writing direction;
* puzzle solved: grid lights up subtly;
* stamp earned: stamp presses onto stamp card;
* unlock: cargo box opens calmly.

REWARDS
Create two reward systems.

1. Digital unlocks:

* planets;
* stars;
* rocket parts;
* cockpit elements;
* space badges;
* funny collectibles such as space poops;
* badges for difficult puzzles;
* badges for low hint usage;
* badges for strategy use;
* badges for correcting mistakes.

Funny collectible examples:

* Ruimte-kakje;
* Gouden kakje;
* Meteoor-kakje;
* Alien-kakje;
* Supernova-kakje.

Keep this playful, not vulgar.

2. Parent-defined rewards:
   Use a digital stamp card.

Default reward tiers:

* Tier 1: Je hebt een kleine beloning verdiend.
* Tier 2: Je hebt een snoepje verdiend.
* Tier 3: Je hebt een grote beloning verdiend.

Tier 2 must default to:
“Je hebt een snoepje verdiend.”

Parents must be able to edit reward labels.

Real-life rewards must never be automatically granted. They require parent approval in parent mode.

Create:

* StampCard;
* StampEvent;
* ParentReward;
* RewardRedemption;
* ParentApprovalStatus.

GIFTED CHILD SUPPORT
The app must support gifted children.

Avoid unnecessary repetition.

Add adaptive logic:

* if accuracy is high and hints are low, increase difficulty or offer puzzle enrichment;
* if accuracy is medium, offer guided practice;
* if accuracy is low, go back to visual practice;
* if responses are very fast and correct, offer challenge mode;
* if repeated mistakes occur, offer a micro-lesson;
* if the child skips explanation often, allow faster route;
* if boredom is detected, offer expert orbit or puzzle.

Track:

* accuracy;
* response time;
* hint usage;
* retries;
* language switches;
* translation usage;
* repeated mistakes;
* tracing accuracy;
* drag-and-drop mistakes;
* skipped explanations;
* perfect streaks.

OFFLINE-FIRST + ONLINE PROGRESS SYNC
The app must work offline.

Local:

* profiles;
* settings;
* content;
* progress;
* rewards;
* stamp cards;
* animation settings.

After every exercise:

1. Save ProgressEvent locally.
2. Try to sync online.
3. If sync fails, add event to sync queue.
4. Use WorkManager to retry later.
5. Never block the child.
6. Show sync status to parents.
7. Sync must be per childProfileId.
8. Use event ids and timestamps to avoid duplicate sync.

Create:

* LocalProgressRepository;
* RemoteProgressRepository;
* ProgressSyncQueue;
* SyncWorker;
* SyncStatus;
* LastSyncedAt;
* PendingSyncCount.

For the remote layer, create an interface first. If no backend is configured yet, implement a fake RemoteProgressRepository that simulates success/failure and can later be replaced by Firebase, Supabase or a custom API.

PRIVACY
Because this is a children’s app:

* no ads;
* no tracking;
* no external analytics in MVP;
* no public profiles;
* no chat;
* no leaderboard;
* no unnecessary personal data;
* local-first;
* minimal online sync;
* parent controls profiles;
* use anonymous childProfileId for remote sync.

FIRST IMPLEMENTATION STRATEGY
Do not implement everything at once.

Build the MVP in vertical slices.

PHASE 1 — App skeleton
Create:

* package structure;
* theme;
* navigation;
* placeholder screens that compile;
* Poincaré home screen;
* parent PIN placeholder;
* profile selection placeholder.

PHASE 2 — Models and local storage
Create:

* core Kotlin models;
* Room entities for child profiles and progress events;
* repositories;
* DataStore settings;
* fake sample profile only for development, not hardcoded as final Henri.

PHASE 3 — Profile onboarding
Create:

* create child profile screen;
* choose profile screen;
* edit profile screen;
* save profile locally.

PHASE 4 — Space map
Create:

* calm space-themed home map;
* planets for Letters, Getallen, Puzzels;
* subtle orbit animation;
* reduced-motion support.

PHASE 5 — JSON content loading
Create:

* JSON exercise models;
* ContentRepository;
* sample JSON assets;
* load content from assets;
* no hardcoded exercises in UI.

PHASE 6 — Maths vertical slice
Create:

* one fourth-grade maths exercise renderer;
* custom number pad;
* example multiplication/division exercise;
* example place-value exercise;
* correct/wrong feedback;
* ProgressEvent after answer;
* local save;
* fake online sync attempt.

PHASE 7 — Writing vertical slice
Create:

* letter tracing component;
* start point;
* stroke direction;
* rocket trail animation;
* sample lowercase print letter;
* tracing feedback;
* progress save.

PHASE 8 — Reading vertical slice
Create:

* letter-sound exercise;
* word builder;
* TextToSpeech hook;
* tap-to-translate placeholder;
* progress save.

PHASE 9 — Puzzle vertical slice
Create:

* drag-and-drop digit component;
* simple math crossword;
* simple sudoku-like grid;
* puzzle solved animation;
* progress save.

PHASE 10 — Rewards
Create:

* stamp card;
* digital unlock;
* parent-defined rewards;
* default Tier 2: “Je hebt een snoepje verdiend.”
* parent approval placeholder.

PHASE 11 — Adaptive engine
Create:

* simple AdaptiveDifficultyEngine;
* track accuracy, hints, speed and retries;
* recommend next action.

PHASE 12 — Parent dashboard
Create:

* progress overview;
* profile settings;
* reward settings;
* sync status;
* animation settings;
* language settings.

VERY IMPORTANT CODING RULES

* Use Jetpack Compose only for UI.
* Keep composables small.
* Use ViewModels for screen state.
* Use StateFlow.
* Keep domain logic out of composables.
* Use repositories for data access.
* Use fake implementations where backend is not ready.
* Keep the project buildable after each phase.
* Avoid overengineering the first slice.
* Avoid creating huge files.
* Prefer reusable components.
* Add comments only where helpful.
* Do not introduce unnecessary dependencies.
* Do not create copyrighted learning content.
* Use original example exercises inspired by the described level only.

START NOW
First, inspect the current Android Studio project.

Then implement PHASE 1 only:

* package structure;
* app theme;
* navigation;
* placeholder screens;
* Poincaré home screen;
* calm space style;
* placeholder profile selection;
* placeholder parent mode.

After PHASE 1, stop and summarize:

* files created;
* files modified;
* how to run;
* what the next phase should be.


You are Gemini inside Android Studio. Act as a senior Android architect, Kotlin developer, Jetpack Compose expert, educational app designer, UX designer, animation designer, and curriculum-aware learning product designer for Belgian primary education.

I am building an Android app called “Poincaré”.

Important: do not try to build the full app in one response. Work in small, safe, buildable steps. For every step:

1. Inspect the existing project structure before changing code.
2. Explain briefly what you will add or change.
3. Create or modify only the necessary files.
4. Keep the app compiling after every step.
5. Avoid placeholders that break the build.
6. Use clean Kotlin and Jetpack Compose.
7. Prefer small reusable components.
8. Do not hardcode exercises in UI screens. Learning content should come from JSON assets.
9. When you create code, mention which files were created or modified.
10. After each step, suggest the next logical step.

APP CONCEPT
Poincaré is an Android learning app for children. It combines:

* mathematics;
* mathematical puzzles;
* learning to read;
* learning to write.

The first concrete child user will be my son Henri, but the app must not be hardcoded for Henri. The app must support multiple child profiles. I will create Henri’s profile myself inside the app.

Henri’s starting levels are:

* reading: first grade primary school;
* writing: first grade primary school;
* mathematics: fourth grade primary school;
* puzzles: adaptive, starting around fourth-grade maths level but able to scale faster.

The app must first support Dutch/Flemish, then Belgian French, then English.

The app must be offline-first, but after every exercise it should try to save/sync progress online. If there is no internet, progress must be saved locally and synced later.

DESIGN DIRECTION
The visual style should be space-themed, calm, premium, slightly playful, but not too childish.

Use concepts like:

* missions;
* planets;
* orbit;
* rocket;
* cockpit;
* board computer;
* space codes;
* star map;
* cargo;
* unlocks;
* badges.

Avoid:

* babyish mascots;
* chaotic screens;
* loud animations;
* excessive confetti;
* too many bright colors;
* distracting visual clutter.

The app should feel smart and challenging, especially because it must support gifted children.

CORE FEATURES
The MVP must eventually include:

1. Onboarding.
2. Parent PIN.
3. Multiple child profiles.
4. Profile selection.
5. Space map home screen.
6. Reading module.
7. Writing module.
8. Maths module.
9. Puzzle module.
10. Reward/stamp-card module.
11. Parent dashboard.
12. Local progress tracking.
13. Online progress sync after every exercise.
14. Animation settings.
15. Language support.
16. JSON-driven content.

TECH STACK
Use:

* Kotlin;
* Jetpack Compose;
* Material 3;
* single-activity architecture;
* ViewModel;
* StateFlow;
* unidirectional data flow;
* repository pattern;
* Room for local progress;
* DataStore for settings;
* JSON files in assets for learning content;
* Android TextToSpeech or prepared audio hooks;
* WorkManager for background sync;
* Compose animations;
* Canvas where useful for letter tracing;
* offline-first architecture.

PROJECT STRUCTURE
Create or evolve the project toward this package structure:

ui/
onboarding/
profile/
home/
spaceMap/
math/
reading/
writing/
puzzle/
rewards/
parent/
settings/
components/
animations/

domain/
exercise/
puzzle/
reading/
writing/
adaptive/
rewards/
language/
sync/

data/
content/
progress/
profile/
rewards/
settings/
sync/

model/

CORE DOMAIN MODEL
The app must support multiple child profiles.

Create models similar to:

ChildProfile:

* id;
* name;
* age;
* avatarId;
* rocketColorId;
* appLanguage;
* exerciseLanguage;
* supportLanguage;
* speechLanguage;
* readingLevel;
* writingLevel;
* mathLevel;
* puzzleLevel;
* giftedModeEnabled;
* createdAt;
* updatedAt.

ProgressEvent:

* id;
* childProfileId;
* exerciseId;
* domain;
* skill;
* timestamp;
* answerCorrect;
* responseTimeMs;
* hintsUsed;
* languageSwitches;
* translationsUsed;
* retryCount;
* rewardStampsEarned;
* synced.

ParentReward:

* id;
* childProfileId;
* tier;
* requiredStamps;
* labelNl;
* labelFr;
* labelEn;
* requiresParentApproval;
* isActive.

AnimationSettings:

* animationsEnabled;
* reducedMotionEnabled;
* soundEffectsEnabled;
* hapticFeedbackEnabled;
* unlockAnimationsEnabled;
* maxAnimationIntensity.

LANGUAGE FEATURES
The app must support:

* app language;
* exercise language;
* support language;
* speech language;
* switching support language during exercises;
* tap-to-translate for language-bound exercises;
* parent control to disable translation;
* parent control to force a target language.

For maths exercises, switching language during an exercise is allowed.

For reading exercises, translation is only a support layer. It must not replace the reading goal.

Example:
Target language: French.
Word: chat.
The child hears the French pronunciation.
The child reads the French word.
On tap, the app may show the Dutch meaning “kat”.
But the exercise remains a French reading exercise.

EDUCATIONAL CONTENT
Use curriculum-aware design inspired by Belgian primary education.

Reading Dutch/Flemish, first-grade level:

* lowercase letters;
* uppercase/print letters;
* letter-sound correspondence;
* phonemes;
* graphemes;
* short vowels;
* long vowels;
* digraphs;
* schwa/domme e/doffe e;
* building simple words;
* reading simple sentences;
* simple comprehension.

Writing Dutch/Flemish, first-grade level:

* letter recognition;
* print letters;
* tracing letters;
* start point;
* writing direction;
* stroke order;
* copying;
* completing words;
* building short words;
* optional finger/stylus tracing.

Maths, fourth-grade level:

* number sense;
* mental arithmetic;
* addition and subtraction with larger numbers;
* multiplication;
* division;
* multiplication tables;
* division tables;
* mixed tables;
* open exercises;
* place value;
* building numbers from ten-thousands, thousands, hundreds, tens and units;
* fractions;
* simple decimals if appropriate;
* measurement;
* geometry;
* patterns;
* logical reasoning;
* simple word problems;
* relation between multiplication and division.

Puzzles:

* sudoku;
* math crossword;
* number wall;
* magic squares;
* balance puzzles;
* operator puzzles;
* sequences;
* Calcudoku/KenKen-like puzzles;
* code breaker puzzles;
* drag-and-drop number puzzles.

CONTENT SOURCE
Learning content must be loaded from JSON files in assets.

Do not hardcode exercises directly in Compose screens.

Create JSON schemas or Kotlin serializable data models for:

* exercise id;
* locale;
* supported locales;
* domain;
* skill;
* section;
* level;
* schoolLevel;
* difficulty;
* inputMode;
* prompt;
* speech;
* translation;
* visual assets;
* audio assets;
* animation metadata;
* answer model;
* hints;
* feedback;
* curriculum references;
* tags;
* estimated time;
* mastery weight;
* reward metadata;
* adaptive metadata.

Sections:

* practice;
* exercises;
* puzzles;
* tests.

Input modes:

* custom_number_pad;
* fraction_input;
* decimal_input;
* drag_drop_digits;
* drag_drop_letters;
* tap_choice;
* listen_and_select;
* build_word;
* trace_letter;
* match_pairs;
* tap_to_translate;
* math_crossword;
* sudoku_grid;
* number_wall;
* balance_puzzle;
* operator_puzzle.

INPUT RULES
Never use the standard Android keyboard for number exercises.

For number answers, create a custom number pad with:

* digits 0 to 9;
* delete;
* confirm;
* optional comma;
* optional minus;
* optional fraction mode;
* large clear buttons;
* subtle animation;
* optional haptic feedback.

For puzzles, use drag-and-drop.

For writing, use tracing with finger or stylus where possible.

For letters and words, use tiles, tapping, dragging, listening and building.

ANIMATIONS
Animations are an essential part of the UX, but they must be calm and functional.

General animation rules:

* meaningful;
* short;
* not distracting;
* accessible;
* configurable;
* reduced-motion support;
* no excessive confetti;
* no loud chaotic effects.

Create reusable animation components such as:

* MissionStartAnimation;
* CorrectAnswerAnimation;
* IncorrectAnswerSignalAnimation;
* HintPanelAnimation;
* DragLiftAnimation;
* DropSnapAnimation;
* LetterTraceRocketAnimation;
* LetterSoundPulseAnimation;
* WordCompleteAnimation;
* PuzzleSolvedAnimation;
* TestScanAnimation;
* StampCardAnimation;
* UnlockRevealAnimation;
* SpaceMapOrbitAnimation;
* ReducedMotionAnimationSet.

Animation behavior:

* correct answer: subtle star particles, soft orbit pulse;
* wrong answer: soft signal noise, gentle shake;
* hint: board computer panel slides in;
* drag item: tile lifts slightly;
* correct drop: tile snaps into place;
* wrong drop: tile gently returns;
* letter tracing: rocket trail shows writing direction;
* puzzle solved: grid lights up subtly;
* stamp earned: stamp presses onto stamp card;
* unlock: cargo box opens calmly.

REWARDS
Create two reward systems.

1. Digital unlocks:

* planets;
* stars;
* rocket parts;
* cockpit elements;
* space badges;
* funny collectibles such as space poops;
* badges for difficult puzzles;
* badges for low hint usage;
* badges for strategy use;
* badges for correcting mistakes.

Funny collectible examples:

* Ruimte-kakje;
* Gouden kakje;
* Meteoor-kakje;
* Alien-kakje;
* Supernova-kakje.

Keep this playful, not vulgar.

2. Parent-defined rewards:
   Use a digital stamp card.

Default reward tiers:

* Tier 1: Je hebt een kleine beloning verdiend.
* Tier 2: Je hebt een snoepje verdiend.
* Tier 3: Je hebt een grote beloning verdiend.

Tier 2 must default to:
“Je hebt een snoepje verdiend.”

Parents must be able to edit reward labels.

Real-life rewards must never be automatically granted. They require parent approval in parent mode.

Create:

* StampCard;
* StampEvent;
* ParentReward;
* RewardRedemption;
* ParentApprovalStatus.

GIFTED CHILD SUPPORT
The app must support gifted children.

Avoid unnecessary repetition.

Add adaptive logic:

* if accuracy is high and hints are low, increase difficulty or offer puzzle enrichment;
* if accuracy is medium, offer guided practice;
* if accuracy is low, go back to visual practice;
* if responses are very fast and correct, offer challenge mode;
* if repeated mistakes occur, offer a micro-lesson;
* if the child skips explanation often, allow faster route;
* if boredom is detected, offer expert orbit or puzzle.

Track:

* accuracy;
* response time;
* hint usage;
* retries;
* language switches;
* translation usage;
* repeated mistakes;
* tracing accuracy;
* drag-and-drop mistakes;
* skipped explanations;
* perfect streaks.

OFFLINE-FIRST + ONLINE PROGRESS SYNC
The app must work offline.

Local:

* profiles;
* settings;
* content;
* progress;
* rewards;
* stamp cards;
* animation settings.

After every exercise:

1. Save ProgressEvent locally.
2. Try to sync online.
3. If sync fails, add event to sync queue.
4. Use WorkManager to retry later.
5. Never block the child.
6. Show sync status to parents.
7. Sync must be per childProfileId.
8. Use event ids and timestamps to avoid duplicate sync.

Create:

* LocalProgressRepository;
* RemoteProgressRepository;
* ProgressSyncQueue;
* SyncWorker;
* SyncStatus;
* LastSyncedAt;
* PendingSyncCount.

For the remote layer, create an interface first. If no backend is configured yet, implement a fake RemoteProgressRepository that simulates success/failure and can later be replaced by Firebase, Supabase or a custom API.

PRIVACY
Because this is a children’s app:

* no ads;
* no tracking;
* no external analytics in MVP;
* no public profiles;
* no chat;
* no leaderboard;
* no unnecessary personal data;
* local-first;
* minimal online sync;
* parent controls profiles;
* use anonymous childProfileId for remote sync.

FIRST IMPLEMENTATION STRATEGY
Do not implement everything at once.

Build the MVP in vertical slices.

PHASE 1 — App skeleton
Create:

* package structure;
* theme;
* navigation;
* placeholder screens that compile;
* Poincaré home screen;
* parent PIN placeholder;
* profile selection placeholder.

PHASE 2 — Models and local storage
Create:

* core Kotlin models;
* Room entities for child profiles and progress events;
* repositories;
* DataStore settings;
* fake sample profile only for development, not hardcoded as final Henri.

PHASE 3 — Profile onboarding
Create:

* create child profile screen;
* choose profile screen;
* edit profile screen;
* save profile locally.

PHASE 4 — Space map
Create:

* calm space-themed home map;
* planets for Letters, Getallen, Puzzels;
* subtle orbit animation;
* reduced-motion support.

PHASE 5 — JSON content loading
Create:

* JSON exercise models;
* ContentRepository;
* sample JSON assets;
* load content from assets;
* no hardcoded exercises in UI.

PHASE 6 — Maths vertical slice
Create:

* one fourth-grade maths exercise renderer;
* custom number pad;
* example multiplication/division exercise;
* example place-value exercise;
* correct/wrong feedback;
* ProgressEvent after answer;
* local save;
* fake online sync attempt.

PHASE 7 — Writing vertical slice
Create:

* letter tracing component;
* start point;
* stroke direction;
* rocket trail animation;
* sample lowercase print letter;
* tracing feedback;
* progress save.

PHASE 8 — Reading vertical slice
Create:

* letter-sound exercise;
* word builder;
* TextToSpeech hook;
* tap-to-translate placeholder;
* progress save.

PHASE 9 — Puzzle vertical slice
Create:

* drag-and-drop digit component;
* simple math crossword;
* simple sudoku-like grid;
* puzzle solved animation;
* progress save.

PHASE 10 — Rewards
Create:

* stamp card;
* digital unlock;
* parent-defined rewards;
* default Tier 2: “Je hebt een snoepje verdiend.”
* parent approval placeholder.

PHASE 11 — Adaptive engine
Create:

* simple AdaptiveDifficultyEngine;
* track accuracy, hints, speed and retries;
* recommend next action.

PHASE 12 — Parent dashboard
Create:

* progress overview;
* profile settings;
* reward settings;
* sync status;
* animation settings;
* language settings.

VERY IMPORTANT CODING RULES

* Use Jetpack Compose only for UI.
* Keep composables small.
* Use ViewModels for screen state.
* Use StateFlow.
* Keep domain logic out of composables.
* Use repositories for data access.
* Use fake implementations where backend is not ready.
* Keep the project buildable after each phase.
* Avoid overengineering the first slice.
* Avoid creating huge files.
* Prefer reusable components.
* Add comments only where helpful.
* Do not introduce unnecessary dependencies.
* Do not create copyrighted learning content.
* Use original example exercises inspired by the described level only.

START NOW
First, inspect the current Android Studio project.

Then implement PHASE 1 only:

* package structure;
* app theme;
* navigation;
* placeholder screens;
* Poincaré home screen;
* calm space style;
* placeholder profile selection;
* placeholder parent mode.

After PHASE 1, stop and summarize:

* files created;
* files modified;
* how to run;
* what the next phase should be.

## PHASE 2 — Core models and local storage

Implement PHASE 2 only.

Before changing code:

1. Inspect the current project structure.
2. Check what was already created in PHASE 1.
3. Keep the app compiling.

Goal:
Create the core data model and local storage foundation for Poincaré.

Add or update the following:

1. Core models:

* ChildProfile
* ProgressEvent
* ParentReward
* StampCard
* StampEvent
* RewardRedemption
* AnimationSettings
* LocaleCode
* SkillLevel
* LearningDomain
* ExerciseSection
* InputMode
* SyncStatus
* ParentApprovalStatus

2. Local database:
   Use Room for:

* child profiles;
* progress events;
* stamp cards;
* parent rewards;
* sync queue events.

3. DataStore:
   Use DataStore for:

* selected profile id;
* parent PIN placeholder state;
* global app settings;
* animation settings;
* language settings.

4. Repositories:
   Create interfaces and local implementations:

* ProfileRepository
* ProgressRepository
* RewardsRepository
* SettingsRepository

5. Fake development data:
   You may create fake sample data for development, but do not hardcode Henri as the final user. The app must support multiple profiles.

Rules:

* Keep domain models separate from Room entities where useful.
* Use clean mapping functions.
* Do not implement UI beyond what is needed to keep the app compiling.
* Do not add online sync yet, except for placeholder interfaces if useful.
* Keep files small and organized.
* After finishing, summarize all created and modified files.
* Stop after PHASE 2.

## PHASE 3 — Profile onboarding and profile selection

Implement PHASE 3 only.

Before changing code:

1. Inspect the current project.
2. Check the models, Room setup and repositories from PHASE 2.
3. Keep the app compiling.

Goal:
Create profile onboarding and profile selection for multiple child profiles.

Build:

1. CreateProfileScreen

* name input;
* optional age input;
* avatar selection placeholder;
* rocket color selection placeholder;
* app language;
* exercise language;
* support language;
* speech language;
* reading level;
* writing level;
* maths level;
* puzzle level;
* gifted mode toggle.

2. ProfileSelectionScreen

* list all local child profiles;
* allow selecting a profile;
* allow creating a new profile;
* show selected profile clearly.

3. EditProfileScreen placeholder

* enough structure to expand later;
* do not overbuild.

4. ViewModel logic

* ProfileSelectionViewModel;
* CreateProfileViewModel;
* use ProfileRepository;
* persist selected profile id in DataStore.

5. Navigation

* if no profile exists, go to create profile;
* if profiles exist, show profile selection;
* after selecting a profile, go to the space map/home screen.

Rules:

* Do not hardcode Henri.
* Use sample avatars/rocket colors as simple ids or enum values.
* Keep UI calm and space-themed.
* No online functionality yet.
* Keep Compose components small.
* After finishing, summarize all created and modified files.
* Stop after PHASE 3.

## PHASE 4 — Space map home screen

Implement PHASE 4 only.

Before changing code:

1. Inspect the existing project.
2. Check the profile selection flow.
3. Keep the app compiling.

Goal:
Create the first real Poincaré home experience: a calm space map with planets.

Build:

1. SpaceMapScreen

* show selected child profile name;
* show calm space-themed background;
* show three first planets:

  * Letters;
  * Numbers;
  * Puzzles.
* each planet is clickable;
* clicking navigates to placeholder module screens if those screens are not implemented yet.

2. SpaceMapViewModel

* load selected profile;
* expose screen state;
* handle navigation events if needed.

3. Animations

* subtle orbit animation;
* subtle planet hover/pulse;
* reduced motion support using AnimationSettings;
* no loud effects;
* no excessive confetti.

4. Navigation

* connect SpaceMapScreen to:

  * Reading placeholder;
  * Math placeholder;
  * Puzzle placeholder;
  * Parent dashboard placeholder;
  * Rewards placeholder if already present.

5. Reusable components

* SpaceBackground;
* PlanetButton;
* OrbitRing;
* ProfileHeader.

Rules:

* Keep the visual style premium, calm and slightly playful.
* Do not add real exercise logic yet.
* Keep animations configurable.
* If reduced motion is enabled, use static UI or simple fades.
* After finishing, summarize all created and modified files.
* Stop after PHASE 4.

## PHASE 5 — JSON content loading

Implement PHASE 5 only.

Before changing code:

1. Inspect the project.
2. Check existing model classes.
3. Keep the app compiling.

Goal:
Create JSON-driven learning content loading from assets.

Build:

1. JSON data models for learning content:

* ExerciseContent;
* LocalizedText;
* AnswerModel;
* HintModel;
* FeedbackModel;
* RewardMetadata;
* AnimationMetadata;
* AdaptiveMetadata;
* CurriculumReference;
* PuzzleContent if useful.

2. ContentRepository

* interface;
* AssetContentRepository implementation;
* load JSON from assets;
* parse content safely;
* expose exercises by domain, locale, skill, level and section.

3. Sample asset files:
   Create original sample JSON content, not copied from copyrighted sources.

Add sample content for:

* one Dutch/Flemish fourth-grade multiplication exercise;
* one Dutch/Flemish division exercise;
* one Dutch/Flemish place-value exercise;
* one Dutch/Flemish first-grade letter-sound exercise;
* one Dutch/Flemish writing/tracing exercise;
* one simple drag-and-drop puzzle.

4. Error handling

* if JSON fails to load, return a safe error state;
* do not crash the app.

5. Developer preview/debug

* optionally add a simple debug screen or log output to confirm loaded exercises.

Rules:

* Do not hardcode exercises in Compose screens.
* All future exercises must come from JSON.
* Keep schemas flexible for future FR-BE and EN content.
* Include fields for prompt, speech and translations.
* Include animation metadata.
* Include reward metadata.
* After finishing, summarize all created and modified files.
* Stop after PHASE 5.

## PHASE 6 — Maths vertical slice with custom number pad

Implement PHASE 6 only.

Before changing code:

1. Inspect the existing project.
2. Check JSON content loading from PHASE 5.
3. Keep the app compiling.

Goal:
Build the first complete maths exercise flow.

Build:

1. MathScreen

* load one maths exercise from JSON;
* show prompt;
* show optional spoken prompt button placeholder;
* show answer field;
* use a custom number pad;
* never use the standard Android keyboard.

2. CustomNumberPad component

* digits 0 to 9;
* delete;
* confirm;
* optional comma button disabled by default;
* optional fraction button placeholder;
* large clear buttons;
* subtle press animation;
* optional haptic hook.

3. Answer checking

* compare input with answer model;
* show correct feedback;
* show incorrect feedback;
* allow retry;
* track retry count;
* track response time;
* track hints used.

4. Feedback animations

* correct answer: subtle star/orbit pulse;
* wrong answer: soft signal noise or gentle shake;
* reduced motion fallback.

5. Progress save
   After answer confirmation:

* create ProgressEvent;
* save locally via ProgressRepository;
* mark synced = false for now;
* do not block the UI.

6. Navigation

* allow returning to SpaceMapScreen;
* allow loading next sample maths exercise if available.

Rules:

* Keep domain logic outside composables.
* Use ViewModel + StateFlow.
* Do not hardcode the exercise in UI.
* Use JSON content.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 6.

## PHASE 7 — Fake online progress sync after every exercise

Implement PHASE 7 only.

Before changing code:

1. Inspect the current progress saving logic.
2. Check the local ProgressEvent storage.
3. Keep the app compiling.

Goal:
Add online progress sync architecture without needing a real backend yet.

Build:

1. RemoteProgressRepository interface

* syncProgressEvent(event: ProgressEvent): Result<Unit>
* syncPendingEvents(): Result<Unit> or similar.

2. FakeRemoteProgressRepository

* simulate successful sync by default;
* allow future failure simulation;
* do not use a real backend yet.

3. ProgressSyncQueue

* store unsynced ProgressEvents locally;
* after every exercise, try remote sync;
* if successful, mark event as synced;
* if failed, keep event pending.

4. WorkManager

* create SyncWorker;
* retry pending sync with backoff;
* run when network is available if possible.

5. Sync status
   Expose to parent/dashboard later:

* lastSyncedAt;
* pendingSyncCount;
* lastSyncError if useful.

6. Integrate into maths flow
   After a maths exercise:

* save locally first;
* try sync;
* never block the child;
* if sync fails, continue normally.

Rules:

* No real backend yet.
* Keep remote implementation replaceable by Firebase, Supabase or custom API later.
* Do not add authentication yet.
* Use anonymous childProfileId.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 7.

## PHASE 8 — Writing vertical slice with letter tracing

Implement PHASE 8 only.

Before changing code:

1. Inspect the project.
2. Check content loading and profile/progress structure.
3. Keep the app compiling.

Goal:
Create the first writing/tracing exercise for first-grade Dutch/Flemish print letters.

Build:

1. WritingScreen

* load one writing exercise from JSON;
* show target lowercase print letter;
* show start point;
* show writing direction;
* show writing guide lines;
* show prompt and optional speech button.

2. LetterTracingComponent

* use Canvas or Compose pointer input;
* support finger/stylus tracing;
* show a rocket trail animation for the model stroke;
* allow child to trace over the letter;
* show progress visually;
* give simple feedback.

3. Tracing logic
   For MVP, keep tracing evaluation simple:

* started near the start point;
* moved in roughly correct direction;
* reached enough of the path;
* do not require perfect fine motor control.

4. Animations

* start point pulses;
* rocket trail shows direction;
* successful trace shows small star trail;
* incorrect start shows gentle hint;
* reduced motion fallback.

5. Progress save
   After completion:

* create ProgressEvent;
* save locally;
* attempt fake online sync;
* include tracing-related metadata if the model already supports it, otherwise keep it simple.

Rules:

* Do not make tracing frustrating.
* Do not require perfect handwriting.
* Focus on first-grade writing movement.
* Use original sample content.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 8.

## PHASE 9 — Reading vertical slice with letter-sound and word builder

Implement PHASE 9 only.

Before changing code:

1. Inspect the project.
2. Check JSON content loading.
3. Check progress saving and fake sync.
4. Keep the app compiling.

Goal:
Create the first reading exercises for Dutch/Flemish first-grade level.

Build:

1. ReadingScreen

* load reading content from JSON;
* show one letter-sound exercise;
* show one word builder exercise if available.

2. LetterSoundRenderer

* show lowercase letter;
* show optional uppercase/print variant;
* play or placeholder speech for the sound;
* letter pulses softly when sound is played;
* child selects matching letter or sound.

3. WordBuilderRenderer

* show target spoken word or image placeholder;
* show letter tiles;
* child drags or taps letters into order;
* word is pronounced when complete;
* show feedback.

4. TextToSpeech hook

* create a TTS service/interface;
* use Android TextToSpeech if feasible;
* otherwise create a clean placeholder interface for later;
* support locale codes.

5. Tap-to-translate placeholder

* for now, implement a small translation/help panel;
* show translation only if content contains translation;
* keep reading goal central.

6. Progress save
   After each reading exercise:

* save ProgressEvent locally;
* attempt fake online sync;
* track hints, retries and translationsUsed.

Rules:

* Translation must support comprehension, not replace reading.
* Do not overbuild FR-BE or EN yet.
* Keep UI calm and child-friendly.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 9.

## PHASE 10 — Puzzle vertical slice with drag-and-drop digits

Implement PHASE 10 only.

Before changing code:

1. Inspect the project.
2. Check drag/drop support if already added.
3. Keep the app compiling.

Goal:
Create the first mathematical puzzle experience.

Build:

1. PuzzleScreen

* load puzzle JSON content;
* show puzzle type selector or first available puzzle;
* support at least one drag-and-drop digit puzzle.

2. DragDropDigitComponent

* digit tiles;
* target cells;
* tile lifts on drag;
* target highlights on hover;
* correct drop snaps into place;
* wrong drop gently returns;
* reduced motion fallback.

3. Simple Math Crossword

* create a small grid;
* use digit tiles;
* check horizontal/vertical equations;
* show partial correctness if useful;
* avoid standard keyboard.

4. Simple Sudoku-like Grid

* small 4x4 grid for MVP;
* drag digits into empty cells;
* check constraints;
* allow reset.

5. Puzzle solved animation

* subtle grid glow;
* star line or space code unlock;
* no excessive confetti.

6. Progress save
   After puzzle completion:

* save ProgressEvent;
* attempt fake online sync;
* reward stamps if configured.

Rules:

* Keep puzzle content JSON-driven.
* Do not hardcode puzzle values in UI.
* Keep drag-and-drop reusable.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 10.

## PHASE 11 — Rewards and stamp card

Implement PHASE 11 only.

Before changing code:

1. Inspect progress and reward models.
2. Check ProfileRepository and RewardsRepository.
3. Keep the app compiling.

Goal:
Create the first reward and stamp-card system.

Build:

1. RewardScreen

* show current profile’s stamp card;
* show current stamp count;
* show next reward tier;
* show digital unlocks placeholder.

2. StampCard component

* visual stamp card;
* stamp press animation;
* show progress toward next tier;
* reduced motion fallback.

3. Default parent-defined reward tiers
   Create defaults per profile:

* Tier 1: “Je hebt een kleine beloning verdiend.”
* Tier 2: “Je hebt een snoepje verdiend.”
* Tier 3: “Je hebt een grote beloning verdiend.”

Important:
Tier 2 must default exactly to:
“Je hebt een snoepje verdiend.”

4. Parent approval model

* reward redemptions require parent approval;
* create placeholder flow for parent approval;
* do not automatically grant real-life rewards.

5. Digital unlocks
   Create basic structure for:

* planets;
* rocket parts;
* badges;
* space poop collectibles.

Add one sample digital unlock:

* “Ruimte-kakje” or similar.

6. Integration

* when exercises earn stamps, update stamp card;
* if a tier is reached, show reward pending parent approval.

Rules:

* Parent reward labels must be editable later.
* Do not hardcode reward UI only; store rewards as data.
* Keep humor playful, not vulgar.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 11.

## PHASE 12 — Parent dashboard

Implement PHASE 12 only.

Before changing code:

1. Inspect existing profile, progress, rewards and sync data.
2. Keep the app compiling.

Goal:
Create a parent dashboard protected by a PIN placeholder.

Build:

1. ParentDashboardScreen
   Sections:

* selected profile;
* profile settings;
* learning levels;
* language settings;
* progress overview;
* difficult skills;
* recent progress events;
* reward settings;
* stamp card management;
* sync status;
* animation settings.

2. Parent PIN

* use existing placeholder if present;
* do not implement production-grade security yet;
* keep it simple and local for MVP.

3. Profile settings
   Allow viewing/editing:

* name;
* age;
* reading level;
* writing level;
* math level;
* puzzle level;
* gifted mode.

4. Reward settings
   Allow viewing/editing:

* Tier 1 label;
* Tier 2 label;
* Tier 3 label;
* required stamps;
* active/inactive.

Ensure Tier 2 default remains:
“Je hebt een snoepje verdiend.”

5. Sync status
   Show:

* last successful sync;
* pending sync count;
* last sync error if available.

6. Animation settings
   Allow toggling:

* animations enabled;
* reduced motion;
* sound effects;
* haptic feedback;
* unlock animations;
* animation intensity.

Rules:

* Parent dashboard can be basic but functional.
* Avoid complex charts for now.
* Keep data local.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 12.

## PHASE 13 — Adaptive difficulty engine

Implement PHASE 13 only.

Before changing code:

1. Inspect progress events and exercise models.
2. Keep the app compiling.

Goal:
Create a simple adaptive engine for gifted-child support.

Build:

1. AdaptiveDifficultyEngine
   Input:

* recent ProgressEvents;
* current child profile;
* current skill;
* current domain;
* current exercise difficulty.

Output:

* repeatPractice;
* guidedPractice;
* independentPractice;
* offerPuzzle;
* increaseDifficulty;
* decreaseDifficulty;
* offerMicroLesson;
* offerChallengeMode;
* skipAheadSuggestion.

2. Rules
   Implement simple MVP rules:

* accuracy >= 85% and low hint usage: increase difficulty or offer puzzle;
* accuracy 60–85%: guided practice;
* accuracy < 60%: return to visual practice;
* very fast correct answers: offer challenge mode;
* repeated mistake pattern: offer micro-lesson;
* many retries: offer guided practice;
* many translation uses: keep support language available;
* perfect streak: offer expert orbit.

3. Integration

* show recommendation after a short session or mini test;
* do not interrupt every exercise;
* use calm wording.

4. Data

* compute from local ProgressEvents;
* no online dependency.

Rules:

* Keep the engine deterministic and easy to test.
* Keep UI integration minimal.
* Do not overbuild machine learning.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 13.

## PHASE 14 — Mini tests / mission scans

Implement PHASE 14 only.

Before changing code:

1. Inspect exercise flow and AdaptiveDifficultyEngine.
2. Keep the app compiling.

Goal:
Create short adaptive tests called “mission scans”.

Build:

1. MiniTestScreen

* load 5 to 10 exercises for a skill/domain;
* show one question at a time;
* use the correct renderer per inputMode;
* save progress per question;
* summarize result at the end.

2. TestScanAnimation

* scanner bar over result card;
* calm result reveal;
* reduced motion fallback.

3. Results
   Show:

* score;
* accuracy;
* hints used;
* retries;
* recommendation from AdaptiveDifficultyEngine;
* next suggested action.

4. Navigation
   From result:

* continue practice;
* try puzzle;
* increase difficulty;
* return to space map.

Rules:

* Do not make it feel like a stressful exam.
* Use “mission scan” language.
* Keep it short.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 14.

## PHASE 15 — Language settings and tap-to-translate refinement

Implement PHASE 15 only.

Before changing code:

1. Inspect language models and reading flow.
2. Keep the app compiling.

Goal:
Improve language support and tap-to-translate.

Build:

1. Language settings
   Allow per profile:

* appLanguage;
* exerciseLanguage;
* supportLanguage;
* speechLanguage;
* allowLanguageSwitchDuringExercise;
* allowTapToTranslate;
* showTranslationAfterAttempt;
* parentCanDisableTranslation;
* parentCanForceTargetLanguage.

2. TapToTranslateTextRenderer

* render words as tappable spans or composables;
* on tap, show translation if available;
* long press can show support panel if easier;
* support reduced distraction.

3. Reading constraints
   For reading exercises:

* translation is a support layer only;
* keep target word/letter central;
* do not automatically reveal translation before attempt unless setting allows it.

4. Maths constraints
   For maths:

* allow exercise language switching more freely;
* preserve the same exercise content/answer.

5. Parent controls
   Add parent settings placeholders if not already present.

Rules:

* Do not use online translation APIs yet.
* Use translations stored in JSON.
* Keep FR-BE and EN structurally supported, even if content is limited.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 15.

## PHASE 16 — Animation system refinement

Implement PHASE 16 only.

Before changing code:

1. Inspect existing animation components.
2. Inspect AnimationSettings.
3. Keep the app compiling.

Goal:
Centralize and refine Poincaré’s animation system.

Build:

1. Animation components
   Ensure reusable components exist for:

* MissionStartAnimation;
* CorrectAnswerAnimation;
* IncorrectAnswerSignalAnimation;
* HintPanelAnimation;
* DragLiftAnimation;
* DropSnapAnimation;
* LetterTraceRocketAnimation;
* LetterSoundPulseAnimation;
* WordCompleteAnimation;
* PuzzleSolvedAnimation;
* TestScanAnimation;
* StampCardAnimation;
* UnlockRevealAnimation;
* SpaceMapOrbitAnimation;
* ReducedMotionAnimationSet.

2. Animation settings
   Use:

* animationsEnabled;
* reducedMotionEnabled;
* soundEffectsEnabled;
* hapticFeedbackEnabled;
* unlockAnimationsEnabled;
* maxAnimationIntensity.

3. Reduced motion
   If reduced motion is enabled:

* replace orbit motion with static visuals or fades;
* remove shake effects;
* avoid particles;
* keep feedback clear.

4. Consistency
   Use consistent timing:

* micro-animations around 100–400 ms;
* unlock animations slightly longer but not excessive.

Rules:

* Do not make UI chaotic.
* No excessive confetti.
* Keep animations meaningful.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 16.

## PHASE 17 — Polish, accessibility and tablet layout

Implement PHASE 17 only.

Before changing code:

1. Inspect the main screens.
2. Keep the app compiling.

Goal:
Polish UX, accessibility and layouts.

Build:

1. Accessibility

* content descriptions;
* large touch targets;
* readable text sizes;
* high contrast where needed;
* reduced motion honored;
* no feedback that depends only on color.

2. Tablet support

* improve layouts for tablets;
* avoid overly stretched UI;
* use two-column layout where helpful;
* keep phone layout usable.

3. Child-friendly UX

* large buttons;
* clear navigation;
* no accidental parent mode entry;
* calm feedback;
* short instructions.

4. Visual polish

* consistent spacing;
* consistent typography;
* calm space theme;
* reusable theme tokens.

5. Error states

* missing content;
* no selected profile;
* sync failure;
* empty progress;
* no rewards yet.

Rules:

* Do not add new large features.
* Focus on polish and reliability.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 17.

## PHASE 18 — Prepare for real backend later

Implement PHASE 18 only.

Before changing code:

1. Inspect RemoteProgressRepository and sync code.
2. Keep the app compiling.

Goal:
Prepare the sync layer so a real backend can be added later without rewriting the app.

Build:

1. Remote abstraction
   Ensure all remote sync goes through:

* RemoteProgressRepository;
* DTO models if useful;
* mapper functions;
* no direct backend calls from UI or ViewModel.

2. Fake backend remains default

* keep FakeRemoteProgressRepository active;
* no real API keys;
* no Firebase/Supabase setup unless explicitly asked later.

3. Backend-ready structure
   Prepare for later:

* Firebase implementation;
* Supabase implementation;
* custom REST API implementation.

Only create interfaces/placeholders, not real backend code.

4. Sync safety

* idempotent event ids;
* timestamps;
* per childProfileId;
* pending queue;
* retry with WorkManager;
* duplicate prevention.

Rules:

* Do not add real online dependencies unless necessary.
* Do not add secrets.
* Do not block exercise flow.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 18.

## PHASE 19 — Testing foundation

Implement PHASE 19 only.

Before changing code:

1. Inspect current architecture.
2. Keep the app compiling.

Goal:
Add a basic testing foundation.

Build:

1. Unit tests for:

* AdaptiveDifficultyEngine;
* reward tier logic;
* stamp card logic;
* answer checking;
* content parsing;
* progress event creation.

2. Repository tests where feasible:

* use fake repositories;
* avoid complex instrumentation unless needed.

3. Sample JSON tests

* ensure sample content parses correctly;
* ensure missing optional fields do not crash.

4. Sync tests

* fake successful sync;
* fake failed sync;
* pending queue behavior.

Rules:

* Do not overbuild tests.
* Focus on critical domain logic.
* Keep tests readable.
* Keep the app compiling.
* After finishing, summarize all created and modified files.
* Stop after PHASE 19.

## PHASE 20 — Final MVP review and cleanup

Implement PHASE 20 only.

Before changing code:

1. Inspect the full project.
2. Run or reason through build issues.
3. Keep the app compiling.

Goal:
Clean up the MVP and prepare it for real use/testing.

Tasks:

1. Remove unused code.
2. Remove dead placeholders that are no longer needed.
3. Ensure all screens have safe empty states.
4. Ensure no exercise is hardcoded in UI.
5. Ensure multiple profiles work.
6. Ensure progress is per profile.
7. Ensure local save happens before sync.
8. Ensure fake online sync never blocks child flow.
9. Ensure Tier 2 default reward is exactly:
   “Je hebt een snoepje verdiend.”
10. Ensure reduced motion works.
11. Ensure no standard Android keyboard is used for number exercises.
12. Ensure JSON content loading works.
13. Ensure parent dashboard can view sync status.
14. Ensure package structure is clean.
15. Ensure code is modular and readable.

After cleanup:

* summarize the MVP status;
* list what works;
* list what remains placeholder;
* list recommended next development steps;
* stop after PHASE 20.


