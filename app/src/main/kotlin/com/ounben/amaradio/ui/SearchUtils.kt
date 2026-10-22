package com.ounben.amaradio.ui

object SearchUtils {
    private val WORD_SPLIT_REGEX = Regex("""[\s\-_.\(\)/\[\]!?]+""")
    private val SPACE_SPLIT_REGEX = Regex("""\s+""")

    /**
     * Tiered category search for selection dialogs (Tags, Countries, Languages, Codecs).
     * Strictly prioritizes exact matches and word starts over mid-word substring matches.
     *
     * Tier 1: Exact match (label or code == query) -> Highest priority
     * Tier 2: Starts with query (label or code starts with query) -> Priority 2 + shorter length bonus
     * Tier 3: Word boundary starts with query (a word in label starts with query) -> Priority 3
     * Tier 4: Substring contains query anywhere -> Priority 4
     */
    fun filterAndSortCategories(
        options: List<FilterViewModel.CategoryItem>,
        query: String
    ): List<FilterViewModel.CategoryItem> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return options

        val wordsInQuery = q.split(SPACE_SPLIT_REGEX).filter { it.isNotBlank() }
        if (wordsInQuery.isEmpty()) return options

        val filtered = options.distinctBy { "${it.code}_${it.label}" }.filter { item ->
            val label = item.label.lowercase()
            val code = item.code.lowercase()
            wordsInQuery.all { w -> label.contains(w) || code.contains(w) }
        }

        return filtered.sortedWith(
            compareByDescending<FilterViewModel.CategoryItem> { item ->
                val label = item.label.lowercase().trim()
                val code = item.code.lowercase().trim()
                
                when {
                    // Tier 1: Exact match (e.g. "news" == "news")
                    label == q || code == q -> 100000.0
                    
                    // Tier 2: Starts with query (e.g. "news", "new age" starts with "ne")
                    label.startsWith(q) || code.startsWith(q) -> 50000.0 + (1.0 / (label.length + 1)) * 100.0
                    
                    // Tier 3: Any word inside label starts with query (e.g. "breaking news")
                    label.split(WORD_SPLIT_REGEX).any { it.startsWith(q) } -> 20000.0
                    
                    // Tier 4: Query is contained somewhere inside a word (e.g. "downtempo")
                    else -> 1000.0
                }
            }.thenByDescending { it.count }
             .thenBy { it.label.lowercase() }
        )
    }

    /**
     * Calculates a relevance score for a target name based on multiple query words.
     * Prioritizes word starts and exact matches.
     */
    fun calculateMultiWordScore(targetName: String, queryWords: List<String>): Double {
        if (queryWords.isEmpty()) return 0.0
        val name = targetName.lowercase().trim()
        val wordsInName = name.split(WORD_SPLIT_REGEX).filter { it.isNotEmpty() }
        
        var totalScore = 0.0

        for (q in queryWords) {
            val query = q.lowercase().trim()
            if (query.isEmpty()) continue

            var wordScore = 0.0
            
            wordsInName.forEachIndexed { index, word ->
                if (word.startsWith(query)) {
                    wordScore += if (index == 0) 5000.0 else 2000.0
                    if (word == query) wordScore += 1000.0
                } else if (word.contains(query)) {
                    val pos = word.indexOf(query)
                    wordScore += (1.0 / (pos + 1)) * 100.0
                }
            }

            if (name.startsWith(query)) {
                wordScore += 3000.0
            }

            totalScore += wordScore
        }

        totalScore += (1.0 / (name.length + 1)) * 10.0

        return totalScore
    }

    /** Legacy support for single string queries */
    fun calculateScore(targetName: String, query: String): Double {
        val words = query.split(SPACE_SPLIT_REGEX).filter { it.isNotBlank() }
        return calculateMultiWordScore(targetName, words)
    }
}
