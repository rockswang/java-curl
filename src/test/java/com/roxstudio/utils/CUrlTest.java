package com.roxstudio.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class CUrlTest {

    @Test
    public void httpsViaFiddler() {
        assertTrue(new CUrl("https://www.baidu.com")
                .proxy("127.0.0.1", 8888)
                .opt("-k")
                .exec().length > 0
        );
    }
}
