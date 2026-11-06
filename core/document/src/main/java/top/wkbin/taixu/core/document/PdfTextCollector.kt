package top.wkbin.taixu.core.document

/** Page traversal and text bounds are independent of the platform renderer. No OCR is attempted. */
internal fun collectPdfText(pageCount: Int, limits: ExtractionLimits, pageText: (Int) -> List<String>): DocumentExtraction {
    val budget = ExtractionBudget(limits)
    val text = ExtractedText(limits.maxTextChars)
    try {
        for (index in 0 until minOf(pageCount, limits.maxPages)) {
            budget.check()
            val blocks = pageText(index)
            if (blocks.any { it.isNotBlank() }) {
                text.line(); text.append("[第 ${index + 1} 页]\n", semantic = false)
                for (block in blocks) { budget.check(); text.append(block); text.line() }
            }
        }
        if (pageCount > limits.maxPages) text.truncated = true
    } catch (_: TextFull) { text.truncated = true }
    return text.finish()
}
