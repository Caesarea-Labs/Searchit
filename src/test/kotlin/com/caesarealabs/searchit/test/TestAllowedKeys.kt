package com.caesarealabs.searchit.test

import com.caesarealabs.searchit.DataLens
import com.caesarealabs.searchit.impl.QueryParseResult
import com.caesarealabs.searchit.impl.QueryParser
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import strikt.api.expectThat
import strikt.assertions.isA
import kotlin.test.Test

private data class SimpleTestItem(val x: Int, val y: String)

class TestAllowedKeys {
    private val parser = QueryParser(Lens, listOf())

    private object Lens : DataLens<SimpleTestItem, Int> {
        override fun hasKeyValue(item: SimpleTestItem, key: String, value: String): Boolean = when (key) {
            "x" -> value == item.x.toString()
            "y" -> value == item.y
            else -> error("Unexpected key $key")
        }

        override fun sortKey(item: SimpleTestItem): Comparable<Int> = item.x
        override fun containsText(item: SimpleTestItem, text: String): Boolean = false
        override val keys: Set<String> = hashSetOf("x", "y", "Inconclusive", "Language", "receive")
    }

    @Test
    fun onlyCertainKeysAreAllowed() {
        expectThat(parser.parseQuery("x:3"))
            .isA<Ok<*>>()
        expectThat(parser.parseQuery("y:hello"))
            .isA<Ok<*>>()
        expectThat(parser.parseQuery("Y:hello"))
            .isA<Ok<*>>()
        expectThat(parser.parseQuery("from:today"))
            .isA<Ok<*>>()
        expectThat(parser.parseQuery("fRom:today"))
            .isA<Ok<*>>()
        parser.parseQuery("z:hello").assertError()
        parser.parseQuery("wetwet:hello").assertError()
        parser.parseQuery("inconcusive:hello").assertError()
        parser.parseQuery("languege:hello").assertError()
        parser.parseQuery("recieve:hello").assertError()
    }

    private val printErrors = true

    private fun QueryParseResult.assertError() {
        if (printErrors) println(this)
        expectThat(this).isA<Err<*>>()
    }
}