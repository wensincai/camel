/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin.scribe;

import java.util.concurrent.TimeUnit;

import com.github.kristofa.brave.scribe.ScribeSpanCollector;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.zipkin.ZipkinEventNotifier;
import org.junit.Test;

/**
 * Integration test requires running Zipkin/Scribe running
 *
 * The easiest way is to run using zipkin-docker: https://github.com/openzipkin/docker-zipkin
 *
 * Adjust the IP address to what IP docker-machines have assigned, you can use
 * <tt>docker-machines ls</tt>
 */
public class ZipkinRouteConcurrentScribe extends CamelTestSupport {

    private String ip = "192.168.99.100";
    private ZipkinEventNotifier zipkin;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        zipkin = new ZipkinEventNotifier();
        // we have 2 routes as services
        zipkin.addServiceMapping("seda:foo", "foo");
        zipkin.addServiceMapping("seda:bar", "bar");
        zipkin.setSpanCollector(new ScribeSpanCollector(ip, 9410));
        context.getManagementStrategy().addEventNotifier(zipkin);

        return context;
    }

    @Test
    public void testZipkinRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("seda:foo").whenDone(5).create();

        for (int i = 0; i < 5; i++) {
            template.requestBody("direct:foo", "Hello World");
        }

        assertTrue(notify.matches(60, TimeUnit.SECONDS));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("sed:foo");

                from("seda:foo?concurrentConsumers=2").routeId("foo")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"))
                        .to("seda:bar");

                from("seda:bar?concurrentConsumers=2").routeId("bar")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(0,500)}"));
            }
        };
    }
}