package com.polaris.timetable.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class ScheduleShareFileTest {
    @Test
    public void roundTripKeepsShareLinkExactly() throws Exception {
        String link = "polaris://s?d=zAbCd_123";

        String decoded = ScheduleShareFile.read(new ByteArrayInputStream(
                ScheduleShareFile.encode(link)));

        assertEquals(link, decoded);
    }

    @Test
    public void invalidHeaderIsRejected() {
        byte[] invalid = "polaris://s?d=data".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> ScheduleShareFile.decode(invalid));
    }

    @Test
    public void nonPolarisPayloadCannotBeEncoded() {
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleShareFile.encode("https://example.com/schedule"));
    }
}
