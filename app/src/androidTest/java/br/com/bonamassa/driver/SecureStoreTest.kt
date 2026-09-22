package br.com.bonamassa.driver

import androidx.test.platform.app.InstrumentationRegistry
import br.com.bonamassa.driver.connected.SecureStore
import br.com.bonamassa.driver.client.SavedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SecureStoreTest {
    @Test fun concurrentInstancesPreserveEveryAcknowledgedWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = SecureStore(context)
        val previous = writer.read()
        val initial = SavedState(origin = "https://api.example")
        val pool = Executors.newFixedThreadPool(3)
        val round = CyclicBarrier(3)
        try {
            writer.write(initial)
            val writing = pool.submit {
                val observer = SecureStore(context)
                repeat(32) { index ->
                    round.await(30, TimeUnit.SECONDS)
                    val expected = initial.copy(slug = "checkpoint-$index")
                    writer.write(expected)
                    // A concurrent reader must never delete a write that just succeeded.
                    assertEquals(expected, observer.read())
                }
            }
            val reading = (1..2).map {
                pool.submit {
                    val reader = SecureStore(context)
                    repeat(32) {
                        round.await(30, TimeUnit.SECONDS)
                        repeat(4) { assertNotNull(reader.read()) }
                    }
                }
            }
            writing.get(90, TimeUnit.SECONDS)
            reading.forEach { it.get(90, TimeUnit.SECONDS) }
            assertEquals(initial.copy(slug = "checkpoint-31"), SecureStore(context).read())
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(30, TimeUnit.SECONDS)
            writer.write(previous ?: SavedState())
        }
    }
}

