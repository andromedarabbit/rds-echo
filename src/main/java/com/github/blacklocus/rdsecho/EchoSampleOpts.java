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

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;

public class EchoSampleOpts implements Callable<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(EchoSampleOpts.class);

    @Override
    public Boolean call() throws Exception {

        CompositeConfiguration values = new CompositeConfiguration();

        try {
            PropertiesConfiguration existing = new PropertiesConfiguration();
            existing.setDelimiterParsingDisabled(true);
            existing.load(EchoConst.CONFIGURATION_PROPERTIES);
            values.addConfiguration(existing);
            LOG.info("Preferring values defined in {}", EchoConst.CONFIGURATION_PROPERTIES);
        } catch (ConfigurationException e) {
            LOG.debug("No {} found. Will not include values from any such file.", EchoConst.CONFIGURATION_PROPERTIES);
        }

        PropertiesConfiguration sample = new PropertiesConfiguration();
        sample.setDelimiterParsingDisabled(true);
        sample.load("rdsecho.properties.sample");

        values.addConfiguration(sample);

        StringWriter s = new StringWriter();
        PrintWriter p = new PrintWriter(s);
        p.format("export RDS_ECHO_OPTS=\"%n");

        Field[] f = EchoCfg.class.getDeclaredFields();
        for (Field field : f) {
            if (Modifier.isPublic(field.getModifiers()) &&
                    Modifier.isStatic(field.getModifiers()) &&
                    Modifier.isFinal(field.getModifiers()) &&
                    field.getName().startsWith("PROP_")) {
                String propName = field.get(EchoCfg.class).toString();
                Object templateVal = values.getProperty(propName);
                p.format("    -D%s=%s %n", propName, templateVal);
            }
        }

        p.format("\"%n");

        System.out.println(s);

        LOG.info("Please review the output for any necessary properties that were not been defined.");

        return false;
    }
}
