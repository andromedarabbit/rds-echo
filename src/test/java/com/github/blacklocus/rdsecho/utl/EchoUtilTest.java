/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 BlackLocus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.blacklocus.rdsecho.utl;

import com.amazonaws.services.rds.model.Tag;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class EchoUtilTest {

    @Test
    public void parseTags() {
        String[] rawTags = {"development=yes", "potato=no", "tomato", "pterodactyl=well=maybe"};
        List<Tag> tags = EchoUtil.parseTags(rawTags);

        Assert.assertEquals(3, tags.size());

        Tag one = tags.get(0);
        Assert.assertEquals("development", one.getKey());
        Assert.assertEquals("yes", one.getValue());

        Tag two = tags.get(1);
        Assert.assertEquals("potato", two.getKey());
        Assert.assertEquals("no", two.getValue());

        Tag three = tags.get(2);
        Assert.assertEquals("pterodactyl", three.getKey());
        Assert.assertEquals("well=maybe", three.getValue());
    }
}
