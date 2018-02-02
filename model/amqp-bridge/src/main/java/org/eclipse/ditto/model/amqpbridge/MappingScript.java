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
package org.eclipse.ditto.model.amqpbridge;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * TODO doc
 */
@Immutable
public interface MappingScript extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the content-type of the payloads to map with this MappingScript.
     */
    String getContentType();

    /**
     * E.g. "JavaScript", "Rhino"
     * @return
     */
    String getMappingEngine();

    /**
     *
     * @return
     */
    String getIncomingMappingScript();

    /**
     *
     * @return
     */
    String getOutgoingMappingScript();

    /**
     *
     * @return
     */
    Map<String, String> getOptions();

    /**
     * Returns all non hidden marked fields of this {@code AmqpConnection}.
     *
     * @return a JSON object representation of this AmqpConnection including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code MappingScript}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code content-type} of message for which to apply the mapping.
         */
        public static final JsonFieldDefinition<String> CONTENT_TYPE =
                JsonFactory.newStringFieldDefinition("contentType", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code mappingEngine} to use in order to map messages.
         */
        public static final JsonFieldDefinition<String> MAPPING_ENGINE =
                JsonFactory.newStringFieldDefinition("mappingEngine", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the identifier for the script for incoming messages.
         */
        public static final JsonFieldDefinition<String> INCOMING_MAPPING_SCRIPT =
                JsonFactory.newStringFieldDefinition("incomingMappingScript", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the identifier for the script for outgoing messages.
         */
        public static final JsonFieldDefinition<String> OUTGOING_MAPPING_SCRIPT =
                JsonFactory.newStringFieldDefinition("outgoingMappingScript", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the options for the mapping.
         */
        public static final JsonFieldDefinition<JsonObject> OPTIONS =
                JsonFactory.newJsonObjectFieldDefinition("options", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
