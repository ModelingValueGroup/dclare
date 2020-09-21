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
    public void mapsToJson() {
        assertEquals("{}", toJson(Map.of()));
        assertEquals("{\"a\":1,\"b\":2}", toJson(Map.of(Entry.of("a", 1), Entry.of("b", 2))));
        assertEquals("{\"a\":\"a\"}", toJson(Map.of(Entry.of("a", "a"))));
        assertEquals("{\"a\":null,\"b\":null,\"c\":null}", toJson(Map.of(Entry.of("a", null), Entry.of("b", null), Entry.of("c", null))));
        assertThrows(NullPointerException.class, () -> toJson(Map.of(Entry.of(null, "a"), Entry.of("b", "b"), Entry.of("c", "c"))));
        assertThrows(NullPointerException.class, () -> toJson(Map.of(Entry.of(null, null), Entry.of("null", null))));
        assertThrows(NullPointerException.class, () -> toJson(Map.of(Entry.of(null, null))));
        assertThrows(NullPointerException.class, () -> toJson(Map.of(Entry.of(null, "a"))));
        assertEquals("{\"1\":1,\"b\":2}", toJson(Map.of(Entry.of(1, 1), Entry.of("b", 2))));
        assertEquals("{\"EUR\":1.3,\"SVC\":13.67}", toJson(Map.of(Entry.of(Currency.getInstance("EUR"), 1.3), Entry.of(Currency.getInstance("SVC"), 13.67))));
    }

}
