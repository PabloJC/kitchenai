package com.kitchenai.admin.requests

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestQueryTest {
    private val rare = request("a", "Kombu", count = 1, lastSeenMillis = 30)
    private val common = request("b", "Tomate cherry", count = 5, lastSeenMillis = 10)
    private val recent = request("c", "Jalapeño", count = 5, lastSeenMillis = 20)
    private val dismissed = request("d", "Coca-Cola", count = 9, status = RequestStatus.DISMISSED)
    private val all = listOf(rare, common, recent, dismissed)

    @Test
    fun `open requests come first by count, then by the latest`() {
        assertEquals(listOf(recent, common, rare), all.matching(RequestQuery()))
    }

    @Test
    fun `dismissed requests leave the default list and show under their status`() {
        assertEquals(listOf(dismissed), all.matching(RequestQuery(status = RequestStatus.DISMISSED)))
        assertEquals(4, all.matching(RequestQuery(status = null)).size)
    }

    @Test
    fun `the search ignores case and accents`() {
        assertEquals(listOf(recent), all.matching(RequestQuery(text = "JALAPENO")))
    }
}
