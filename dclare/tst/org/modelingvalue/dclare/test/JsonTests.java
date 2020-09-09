package org.modelingvalue.dclare.test;

import java.util.*;

import org.junit.jupiter.api.*;
import org.modelingvalue.dclare.sync.converter.*;

public class JsonTests {
    @Test
    public void jsonTest() {
        ConvertJson conv = new ConvertJson();

        Map<String, Map<String, String>> o0 =
                Map.of(
                        "john", Map.of(
                                "111", "y1",
                                "222", "y2"
                        ),
                        "paul", Map.of(
                                "333", "y3",
                                "444", "y4",
                                "555", "y5"
                        ),
                        "george", Map.of(
                                "666", "y6",
                                "777", "y7",
                                "888", "y8"
                        ),
                        "ringo", Map.of(
                                "999", "y9"
                        )
                );

        Map<String, Map<String, String>> o2 = conv.convertBackward(conv.convertForward(o0));
        Map<String, Map<String, String>> o4 = conv.convertBackward(conv.convertForward(o2));
        Map<String, Map<String, String>> o6 = conv.convertBackward(conv.convertForward(o4));

        Assertions.assertEquals(o0, o2);
        Assertions.assertEquals(o2, o4);
        Assertions.assertEquals(o4, o6);

        System.err.println("json= " + conv.convertForward(o0));
    }
}
