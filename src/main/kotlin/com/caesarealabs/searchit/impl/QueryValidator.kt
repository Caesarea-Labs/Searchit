package com.caesarealabs.searchit.impl

import com.caesarealabs.searchit.DataLens
import com.caesarealabs.searchit.SpecialFilter
import me.xdrop.fuzzywuzzy.FuzzySearch

internal typealias SpecialFilters = List<SpecialFilter<*>>

internal object QueryValidator {
    context(DataLens<*, *>)
    // Returns null if it is valid
    internal fun validateQuery(query: List<QueryToken>): String? {
        validateParentheses(query)?.let { return it }
        validateOperators(query)?.let { return it }
        validateTime(query, QueryParser.StartDateToken)?.let { return it }
        validateTime(query, QueryParser.EndDateToken)?.let { return it }
        validateKeys(query)?.let { return it }
        return null
    }

    // Check that the specified keys are in the "keys" set
    context(DataLens<*, *>)
    private fun validateKeys(query: List<QueryToken>): String? {
        // Null keys - everything is fine
        val keysValue = keys ?: return null
        val allowedKeys = (keysValue + QueryParser.allDateTokens).map { it.lowercase() }.toHashSet()
        val violator = query.find { it is QueryToken.KeyValue && it.key.lowercase() !in allowedKeys } as QueryToken.KeyValue? ?: return null
        val extraction = FuzzySearch.extractSorted(violator.key, keysValue, 75)
        return "No such key '${violator.key}'.".appendIf(extraction.isNotEmpty()) { " Did you mean: ${extraction.joinToString { "'${it.string}'" }}?" }
    }

    private fun validateOperators(query: List<QueryToken>): String? {
        for ((i, token) in query.withIndex()) {
            if (token is QueryToken.Operator) {
                if (token == QueryToken.Operator.Not) {
                    // The not operator doesn't need an operand to the left, and having 'and not' and 'or not' is valid.
                    if (i == query.size - 1) return "Logical operator '${token} doesn't have an operand to the right"
                    else if (i > 0 && query[i - 1] == QueryToken.Operator.Not) return "'not not' is not valid. "
                } else {
                    when {
                        i == 0 -> return "Logical operator '${token}' doesn't have an operand to the left"
                        i == query.size - 1 -> return "Logical operator '${token} doesn't have an operand to the right"
                        query[i - 1] is QueryToken.Operator -> return "Logical operators '${query[i - 1]}' and '${token}' can't be placed next to each other (in that order)"
                    }
                }

            }
        }
        return null
    }

    private fun validateParentheses(query: List<QueryToken>): String? {
        var openParenthesesCount = 0
        for (token in query) {
            if (token is QueryToken.Parentheses) {
                when (token) {
                    QueryToken.Parentheses.Opening -> {
                        openParenthesesCount++
                    }

                    QueryToken.Parentheses.Closing -> {
                        if (openParenthesesCount == 0) return "Too many closing parentheses"
                        openParenthesesCount--
                    }
                }
            }
        }
        if (openParenthesesCount != 0) {
            return "A parentheses was not closed"
        }
        return null
    }

    private fun validateTime(query: List<QueryToken>, timeToken: String): String? {
        fun isTimeToken(token: QueryToken) = token is QueryToken.KeyValue && token.key == timeToken

        val index = query.indexOfFirst { isTimeToken(it) }
        // If nothing was specified we have nothing to worry about
        if (index == -1) return null
        val lastIndex = query.indexOfLast { isTimeToken(it) }
        if (index != lastIndex) return "'${timeToken}' was specified twice. It may only be specified once."
        // Check logical operator before
        if (index > 0 && query[index - 1] is QueryToken.Operator) {
            return "'${timeToken}' cannot be used with the logical operator '${query[index - 1]}'."
        }
        // Check logical operator after
        if (index < query.size - 1 && query[index + 1] is QueryToken.Operator) {
            return "'${timeToken}' cannot be used with the logical operator '${query[index + 1]}'."
        }

        // Check in parentheses
        var openParenthesesCount = 0
        for (token in query) {
            if (token is QueryToken.Parentheses) {
                when (token) {
                    QueryToken.Parentheses.Opening -> openParenthesesCount++
                    QueryToken.Parentheses.Closing -> openParenthesesCount--
                }
            } else if (isTimeToken(token)) {
                if (openParenthesesCount > 0) {
                    return "'${timeToken}' cannot be used inside of parentheses."
                }
            }
        }
        return null
    }
}