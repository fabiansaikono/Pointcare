package be.saikono.poincare.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseMigrationRegistryTest {
    @Test
    fun `registered migrations provide a continuous non destructive path from 4 to 11`() {
        assertEquals(
            listOf(4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11),
            DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        )
    }
}
