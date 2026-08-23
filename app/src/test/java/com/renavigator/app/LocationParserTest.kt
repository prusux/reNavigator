package com.renavigator.app

import com.renavigator.app.core.model.ParseResult
import com.renavigator.app.core.parser.LocationParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocationParserTest {

    private lateinit var parser: LocationParser

    @Before
    fun setUp() {
        parser = LocationParser()
    }

    @Test
    fun `test trigger tag detection`() {
        assertTrue(parser.hasNavTrigger("Hey let's meet #nav here"))
        assertTrue(parser.hasNavTrigger("#NAV 56.9496, 24.1052"))
        assertTrue(parser.hasNavTrigger("Please go here #Nav"))
        assertFalse(parser.hasNavTrigger("Just a normal text message"))
    }

    @Test
    fun `test standard decimal coordinates`() = runBlocking {
        val message = "Meet at #nav 56.9496, 24.1052 for lunch"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test european comma decimal coordinates`() = runBlocking {
        val message = "#nav 56,9496, 24,1052"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test space separated comma decimal coordinates`() = runBlocking {
        val message = "#nav 56,9496 24,1052"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test cardinal prefix coordinates`() = runBlocking {
        val message = "Pickup point #nav N56.9496, E24.1052"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test cardinal suffix coordinates`() = runBlocking {
        val message = "#nav 56.9496N 24.1052E"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test DMS format coordinates`() = runBlocking {
        val message = "#nav 56°56'58.6\"N 24°06'18.7\"E"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.001)
    }

    @Test
    fun `test google maps web url`() = runBlocking {
        val message = "Check this place #nav https://www.google.com/maps/place/Old+Town/@56.9496,24.1052,17z"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test waze url`() = runBlocking {
        val message = "#nav https://waze.com/ul?ll=56.9496,24.1052&navigate=yes"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test apple maps url`() = runBlocking {
        val message = "#nav https://maps.apple.com/?ll=56.9496,24.1052"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test openstreetmap url`() = runBlocking {
        val message = "#nav https://www.openstreetmap.org/#map=17/56.9496/24.1052"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test geo uri`() = runBlocking {
        val message = "#nav geo:56.9496,24.1052?q=Restaurant"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9496, success.coordinate!!.latitude, 0.0001)
        assertEquals(24.1052, success.coordinate!!.longitude, 0.0001)
    }

    @Test
    fun `test google maps short redirect url`() = runBlocking {
        val message = "#nav https://maps.app.goo.gl/hCgvx2xXgzmoUyBd9?g_st=aw"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertNotNull(success.coordinate)
        assertEquals(56.9918, success.coordinate!!.latitude, 0.005)
        assertEquals(24.1661, success.coordinate!!.longitude, 0.005)
        assertTrue(success.label?.contains("PetCity") == true)
    }

    @Test
    fun `test place name query search fallback`() = runBlocking {
        val message = "#nav Sigulda"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertTrue(success.searchQuery == "Sigulda" || success.coordinate != null)
    }

    @Test
    fun `test venue name query search`() = runBlocking {
        val message = "#nav lokāls karbonādes"
        val result = parser.parse(message)

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals("lokāls karbonādes", success.searchQuery)
    }
}
