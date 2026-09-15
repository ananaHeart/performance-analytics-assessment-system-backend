package com.capstone.assessment.v3.answersheet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class V3DynamicLayoutTest {
    @Test void capacityComesFromPrintableGeometryAndNeverShrinksBubbles() {
        assertEquals(16,V3DynamicLayout.PAGE_CAPACITY);
        assertEquals(192,V3DynamicLayout.MAX_QUESTIONS);
        for(int i=0;i<V3DynamicLayout.PAGE_CAPACITY;i++) {
            double top=V3DynamicLayout.BODY_TOP-i*V3DynamicLayout.ROW_PITCH;
            assertTrue(top-2*V3DynamicLayout.RADIUS>=V3DynamicLayout.BODY_BOTTOM);
        }
        assertTrue(V3DynamicLayout.BODY_TOP-V3DynamicLayout.PAGE_CAPACITY*V3DynamicLayout.ROW_PITCH
                -2*V3DynamicLayout.RADIUS<V3DynamicLayout.BODY_BOTTOM);
        assertEquals(1,V3DynamicLayout.pages(5));assertEquals(1,V3DynamicLayout.pages(7));
        assertEquals(1,V3DynamicLayout.pages(10));assertEquals(1,V3DynamicLayout.pages(16));
        assertEquals(2,V3DynamicLayout.pages(17));assertEquals(12,V3DynamicLayout.pages(192));
        assertThrows(IllegalArgumentException.class,()->V3DynamicLayout.pages(4));
        assertThrows(IllegalArgumentException.class,()->V3DynamicLayout.pages(193));
    }
    @Test void qrBindsAllIdentitiesAndFitsTheByteBudget() throws Exception {
        String sheet=UUID.randomUUID().toString(),page=UUID.randomUUID().toString(),assignment=UUID.randomUUID().toString();
        String payload=V3DynamicLayout.qr(sheet,page,assignment,12,12,V3DynamicLayout.sha256("geometry"));
        var tree=new ObjectMapper().readTree(payload);
        assertTrue(payload.getBytes(StandardCharsets.UTF_8).length<=256);
        assertEquals(3,tree.path("v").asInt());assertEquals("3",tree.path("tv").textValue());
        assertEquals(22,tree.path("as").asText().length());assertEquals(43,tree.path("gh").asText().length());
        assertNotEquals(payload,V3DynamicLayout.qr(sheet,UUID.randomUUID().toString(),assignment,12,12,V3DynamicLayout.sha256("geometry")));
    }
}
