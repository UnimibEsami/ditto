/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.amqpbridge.examplejson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.MappingScript;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionClosed;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionCreated;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionDeleted;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionOpened;

public class JsonExamplesProducer {

    private static final String ID = "myConnection";

    private static final String URI = "amqps://foo:bar@example.com:443";

    private static final AuthorizationSubject AUTHORIZATION_SUBJECT =
            AuthorizationSubject.newInstance("mySolutionId:mySubject");

    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));

    private static final boolean FAILOVER_ENABLED = true;

    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    private static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        producer.produce(Paths.get(args[0]));
    }

    private void produce(final Path rootPath) throws IOException {
        produceAmqpBridgeEvents(rootPath.resolve("amqp-bridge"));
    }

    private static void produceAmqpBridgeEvents(final Path rootPath) throws IOException {
        produceEvents(rootPath);
    }

    private static void produceEvents(final Path rootPath) throws IOException {
        final Path eventsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(eventsDir);

        final AmqpConnection amqpConnection = AmqpBridgeModelFactory.newConnection(ID, URI, AUTHORIZATION_SUBJECT, SOURCES, FAILOVER_ENABLED);
        final DittoHeaders headers = DittoHeaders.empty();

        final MappingScript mappingScript = AmqpBridgeModelFactory.newMappingScript(
                "text/plain",
                "JavaScript",
                "ditto_protocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/modify';" +
                        "ditto_protocolJson.path = '/attributes/foo';" +
                        "ditto_protocolJson.headers = {};" +
                        "ditto_protocolJson.headers['correlation-id'] = mappingHeaders['correlation-id'];" +
                        "ditto_protocolJson.value = mappingString;",
                "mappingString = " +
                        "\"Topic was: \" + dittoProtocolJson.topic + \"\\n\" +\n" +
                        "\"Header correlation-id was: \" + dittoProtocolJson.headers['correlation-id'];",
                Collections.singletonMap("loadMustacheJS", "TRUE")
        );

        final ConnectionCreated connectionCreated = ConnectionCreated.of(amqpConnection,
                Collections.singletonList(mappingScript), headers);
        writeJson(eventsDir.resolve(Paths.get("connectionCreated.json")), connectionCreated);

        final ConnectionOpened connectionOpened = ConnectionOpened.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionOpened.json")), connectionOpened);

        final ConnectionClosed connectionClosed = ConnectionClosed.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionClosed.json")), connectionClosed);

        final ConnectionDeleted connectionDeleted = ConnectionDeleted.of(ID, headers);
        writeJson(eventsDir.resolve(Paths.get("connectionDeleted.json")), connectionDeleted);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {
        final String jsonString = jsonifiable.toJsonString(schemaVersion);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }

}
