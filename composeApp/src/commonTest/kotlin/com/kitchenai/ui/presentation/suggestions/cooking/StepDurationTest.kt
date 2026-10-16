package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StepDurationTest {
    @Test
    fun `english units, spelled out and short`() {
        assertEquals(10.minutes, firstDurationIn("Simmer for 10 minutes."))
        assertEquals(25.minutes, firstDurationIn("Bake 25 min"))
        assertEquals(1.hours, firstDurationIn("Roast 1 h"))
        assertEquals(2.hours, firstDurationIn("Braise 2 hrs"))
        assertEquals(30.seconds, firstDurationIn("Whisk for 30 seconds"))
        assertEquals(10.minutes, firstDurationIn("Give it a 10-minute rest"))
    }

    @Test
    fun `spanish units, spelled out and short`() {
        assertEquals(10.minutes, firstDurationIn("Cocer 10 minutos"))
        assertEquals(1.hours, firstDurationIn("Hornear 1 hora"))
        assertEquals(2.hours, firstDurationIn("Dejar reposar 2 horas"))
        assertEquals(30.seconds, firstDurationIn("Batir 30 segundos"))
        assertEquals(5.minutes, firstDurationIn("Saltear 5 min"))
    }

    @Test
    fun `a range gives its lower bound`() {
        assertEquals(10.minutes, firstDurationIn("Boil 10-15 minutes"))
        assertEquals(2.hours, firstDurationIn("Smoke 2 to 3 hours"))
        assertEquals(10.minutes, firstDurationIn("Cocer entre 10 y 15 minutos"))
        assertEquals(10.minutes, firstDurationIn("Hervir 10 a 15 min"))
    }

    @Test
    fun `decimals read with either separator`() {
        assertEquals(90.minutes, firstDurationIn("Bake 1.5 hours"))
        assertEquals(90.minutes, firstDurationIn("Hornear 1,5 horas"))
    }

    @Test
    fun `a smaller unit right after folds into one timer`() {
        assertEquals(90.minutes, firstDurationIn("Rest 1 hour 30 minutes"))
        assertEquals(90.minutes, firstDurationIn("Rest 1 hour and 30 minutes"))
        assertEquals(90.minutes, firstDurationIn("Reposar 1 hora y 30 minutos"))
    }

    @Test
    fun `a second span of the same unit is a later step, not more of this one`() {
        assertEquals(10.minutes, firstDurationIn("Cook 10 minutes, then 5 minutes more"))
    }

    @Test
    fun `numbers that are not times are ignored`() {
        assertEquals(20.minutes, firstDurationIn("Bake at 180 C for 20 min"))
        assertNull(firstDurationIn("Add 5 minced cloves"))
        assertNull(firstDurationIn("Añadir 5 hojas de laurel"))
        assertNull(firstDurationIn("Stir well"))
    }
}
