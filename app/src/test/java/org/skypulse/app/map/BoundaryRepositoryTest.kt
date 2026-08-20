package org.skypulse.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundaryRepositoryTest {
    @Test
    fun `cached boundary uses MapLibre compatible file URI`() {
        val uri = BoundaryRepository.localFileUri(
            "/data/user_de/0/org.skypulse.app/files/boundaries/fir_boundaries.geojson",
        )

        assertEquals(
            "file:///data/user_de/0/org.skypulse.app/files/boundaries/fir_boundaries.geojson",
            uri.toASCIIString(),
        )
    }
}
