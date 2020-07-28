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
package com.github.blacklocus.rdsecho;

import com.google.common.base.Optional;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EchoCfgTest {

    @Test
    public void checkSample() {
        EchoCfg cfg = new EchoCfg("rdsecho.properties.sample");

        Optional<String[]> newTags = cfg.newTags();
        Assert.assertTrue(newTags.isPresent());

        String[] allNewTags = newTags.get();
        Assert.assertEquals(2, allNewTags.length);

        Assert.assertEquals("orange=false", allNewTags[0]);
        Assert.assertEquals("pear=maybe", allNewTags[1]);

        Optional<String[]> promoteTags = cfg.promoteTags();
        Assert.assertTrue(promoteTags.isPresent());

        String[] allPromoteTags = promoteTags.get();
        Assert.assertEquals(2, allPromoteTags.length);

        Assert.assertEquals("development=yes", allPromoteTags[0]);
        Assert.assertEquals("banana=no", allPromoteTags[1]);
    }
}
