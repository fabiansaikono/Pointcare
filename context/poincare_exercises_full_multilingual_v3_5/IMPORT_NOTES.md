# Import notes for Poincaré Exercise Pack v3.5

- Import the two new files:
  - poincare-exercises-world-knowledge-v3.xml
  - poincare-exercises-geometry-v3.xml
- Keep importing the existing math, language and logic files.
- The old world themes main category is replaced by world_knowledge.
- The old translation subcategory is replaced by translation_to_english and translation_to_french.
- Translation and listening exercises may contain audioSources.
- When audioSources visibleText=false, do not show the source text in the instruction. Show a play button.
- Use audio/@text as TTS input and audio/@ttsLanguage for pronunciation.
- Do not read audioId aloud.
- Audio text is human-readable and contains no underscores.
- Preserve progress by exercise id/progressKey. Existing IDs are stable for unchanged exercises.
