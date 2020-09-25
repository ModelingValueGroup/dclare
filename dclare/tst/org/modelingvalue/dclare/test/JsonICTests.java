package org.modelingvalue.dclare.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.sync.json.JsonIC.*;

import java.util.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.collections.List;
import org.modelingvalue.collections.Map;
import org.modelingvalue.collections.*;

public class JsonICTests {
    @RepeatedTest(1)
    public void listsToJson() {
        assertEquals("[]", toJson(List.of()));
        assertEquals("[1,2,3]", toJson(List.of(1, 2, 3)));
        assertEquals("[1,2,3,[1,2,3,[1,2,3]]]", toJson(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L)))));
        assertEquals("[1,2,3]", toJson(Arrays.asList(1, 2, 3)));
        assertEquals("[1,\"a\",3,12.6,\"q\"]", toJson(List.of(1, "a", 3L, 12.6, 'q')));
    }

    @RepeatedTest(1)
    public void listsFromJson() {
        assertEquals("[]", toJson(List.of()));
        assertEquals("[1,2,3]", toJson(List.of(1, 2, 3)));
        assertEquals("[1,2,3,[1,2,3,[1,2,3]]]", toJson(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L)))));
        assertEquals("[1,2,3]", toJson(Arrays.asList(1, 2, 3)));
        assertEquals("[1,\"a\",3,12.6,\"q\"]", toJson(List.of(1, "a", 3L, 12.6, 'q')));
    }

    @RepeatedTest(1)
    public void arraysFromJson() {
        assertThrows(IllegalArgumentException.class, () -> fromJson("[1,2 3,[1,2,3,[1,2,3]]]"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("[1,2 3,[1,2,3,[1,2,3]],]"));

        assertEquals(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L))), fromJson("[1,2,3,[1,2,3,[1,2,3]]]"));
        assertEquals(List.of(1L, 2L, 3L, List.of(1L, 2L, 3L, List.of(1L, 2L, 3L))), fromJson("   [\n  1,2,\n  3,[\r\t\n    1,\n    2,\n    3,\n    [\n      1,\n      2,\n      3\n    ]\n  ]\n]\n\n\n"));
    }

    @RepeatedTest(1)
    public void mapsFromJson() {
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2,\"x\";}"));
        assertThrows(IllegalArgumentException.class, () -> fromJson("{\"a\":1,\"b\":2;}"));
        assertEquals(Map.of(Entry.of("a", 1L), Entry.of("b", 2L)), fromJson("{\"a\":1,\"b\":2}"));
    }
}
