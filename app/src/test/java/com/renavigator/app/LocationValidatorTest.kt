package com.renavigator.app

import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.ValidationResult
import com.renavigator.app.core.validator.LocationValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocationValidatorTest {

    private lateinit var validator: LocationValidator

    @Before
    fun setUp() {
        validator = LocationValidator()
    }

    @Test
    fun `test invalid coordinate bounds`() {
        val outOfBoundsLat = GeoCoordinate(95.0, 24.10)
        val result1 = validator.validate(destination = outOfBoundsLat, currentLocation = null)
        assertTrue(result1 is ValidationResult.InvalidCoordinates)

        val nullIsland = GeoCoordinate(0.0, 0.0)
        val result2 = validator.validate(destination = nullIsland, currentLocation = null)
        assertTrue(result2 is ValidationResult.InvalidCoordinates)
    }

    @Test
    fun `test distance within max allowed limit`() {
        val riga = GeoCoordinate(56.9496, 24.1052)
        val jurmala = GeoCoordinate(56.9715, 23.7704) // ~22 km away

        val result = validator.validate(
            destination = jurmala,
            currentLocation = riga,
            maxDistanceKm = 400.0
        )

        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertNotNull(valid.distanceKm)
        assertTrue(valid.distanceKm!! in 20.0..25.0)
    }

    @Test
    fun `test distance exceeding max allowed limit`() {
        val riga = GeoCoordinate(56.9496, 24.1052)
        val berlin = GeoCoordinate(52.5200, 13.4050) // ~840 km away

        val result = validator.validate(
            destination = berlin,
            currentLocation = riga,
            maxDistanceKm = 400.0
        )

        assertTrue(result is ValidationResult.ExceedsMaxDistance)
        val exceeds = result as ValidationResult.ExceedsMaxDistance
        assertTrue(exceeds.distanceKm > 800.0)
        assertEquals(400.0, exceeds.maxAllowedKm, 0.001)
    }

    @Test
    fun `test valid search query without coordinates`() {
        val result = validator.validate(
            destination = null,
            searchQuery = "Sigulda",
            currentLocation = null
        )

        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Sigulda", valid.searchQuery)
    }
}
