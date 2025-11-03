package org.example.app.core.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class BaseInstantTypeAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .create();

    @Test
    public void testSerializeInstant() {
        Instant instant = Instant.ofEpochSecond(1681315200L);
        String json = gson.toJson(instant, Instant.class);
        assertEquals("1681315200", json);
    }

    @Test
    public void testDeserializeInstant() {
        Instant instant = gson.fromJson("1681315200", Instant.class);
        assertEquals(Instant.ofEpochSecond(1681315200L), instant);
    }

    @Test
    public void testNullSerialization() {
        String json = gson.toJson(null, Instant.class);
        assertEquals("null", json);
    }

    @Test
    public void testNullDeserialization() {
        Instant instant = gson.fromJson("null", Instant.class);
        assertNull(instant);
    }
}
