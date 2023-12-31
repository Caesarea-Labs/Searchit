package com.caesarealabs.searchit

import com.caesarealabs.searchit.impl.QueryParser
import com.caesarealabs.searchit.impl.search
import com.github.michaelbull.result.getOrElse
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.ceil

/**
 * A note about definitions.
 * A _search_ is the action a user makes to get some results.
 * A _filter_ is a simple condition that may be true or false for a certain element.
 * A _query_ is composed of multiple filters that may be AND'd or OR'd.
 */
public object SearchIt

/**
 * @see Database
 * @see DataLens
 * @see SpecialFilter
 */
public class SearchitContext<T>(internal val database: Database<T>, internal val lens: DataLens<T, *>,
                                private val specialFilters: List<SpecialFilter<T>>) {
    /**
     * Searches through a database with the query [query], paginating with page [page].
     * First construct a [SearchitContext] instance, then call this method.
     */
    public suspend fun search(query: String, page: Int, maxPageItems: Int = 18): SearchitResult<T> {
        val parsedQuery = QueryParser(lens, specialFilters).parseQuery(query).getOrElse { return SearchitResult.SyntaxError(it) }
        val fullSearchResults = search(parsedQuery).sortedByDescending {
            // This cast is fine, it's fair to except all sort keys to have the same type considering that's defined in the DataLens interface.
            @Suppress("UNCHECKED_CAST")
            lens.sortKey(it) as Comparable<Comparable<Nothing>>
        }
        val allPageCount = ceil(fullSearchResults.size.toDouble() / maxPageItems).toInt()
        val actualPage = page.coerceAtMost((allPageCount - 1).coerceAtLeast(0))
        // Return only PageSize items, and skip pages before the requested page
        val pageSearchResults = fullSearchResults.drop(actualPage * maxPageItems).take(maxPageItems)

        return SearchitResult.Success(pageCount = allPageCount, items = pageSearchResults)
    }
}


/**
 * An adapter for a database, for example DynamoDB or ObjectBox
 */
public interface Database<T> {
    //TODO: for better performance, we can consider putting more arguments in this query to allow the database to do more heavy lifting
    // which tends to be more efficient in many cases.
    /**
     * Box-standard filtering of a database by a time range.
     * In this case 'query' is the classic database terminology for it, although this library would normally treat this action as a 'search'.
     */
    public suspend fun query(timeRange: TimeRange): List<T>
}

public data class TimeRange(override val start: Instant, val end: Instant) : ClosedRange<Instant> {
    override val endInclusive: Instant = end
}

/**
 * When filtering items, they may be filtered according to certain characteristics.
 * You must define how we check if a certain item has these characteristics.
 */
public interface DataLens<T, SortKey> {
    /**
     * Returns true if item [item] as the key [key] equal to the value [value]
     * For example the item {x: 2, y:3} could be considered to have the key `x` with the value `2`, but it doesn't have the key `x` with the value `3`.
     */
    public fun hasKeyValue(item: T, key: String, value: String): Boolean

    /**
     * Allows specifying a set of keys so that only they are valid as a key in a `key:value` expression.
     * The keys are not case-sensitive.
     * Return null to allow all keys.
     */
    public val keys: Set<String>? get() = null

    /**
     * The results will be sorted from high to low according to the [sortKey] of each [item].
     * A good sort key is the date of the item.
     */
    public fun sortKey(item: T): Comparable<SortKey>

    /**
     * Needed for text-based search
     */
    public fun containsText(item: T, text: String): Boolean
}

/**
 * Defines special handling for a certain keyword in a `key:value` expression.
 * When `key` is equal to [keyword], the special [filterFactory] logic will be used instead of checking [DataLens.hasKeyValue] like normal.
 * [filterFactory] may return null when the value is invalid, and will return a filter for each item when it is valid.
 */
public data class SpecialFilter<T>(val keyword: String, val filterFactory: (value: String) -> Filter<T>?)

public typealias Filter<T> = (item: T) -> Boolean


@Serializable
public sealed interface SearchitResult<out T> {
    @Serializable
    public data class Success<T>(val pageCount: Int, val items: List<T>) : SearchitResult<T>

    @Serializable
    public data class SyntaxError(val error: String) : SearchitResult<Nothing>
}