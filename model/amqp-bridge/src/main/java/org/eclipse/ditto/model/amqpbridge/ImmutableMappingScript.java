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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link MappingScript}.
 */
@Immutable
final class ImmutableMappingScript implements MappingScript {

    private final String contentType;
    private final String mappingEngine;
    private final String incomingMappingScript;
    private final String outgoingMappingScript;
    private final Map<String, String> options;


    private ImmutableMappingScript(final String contentType, final String mappingEngine,
            final String incomingMappingScript, final String outgoingMappingScript, final Map<String, String> options) {

        this.contentType = contentType;
        this.mappingEngine = mappingEngine;
        this.incomingMappingScript = incomingMappingScript;
        this.outgoingMappingScript = outgoingMappingScript;
        this.options = Collections.unmodifiableMap(new HashMap<>(options));
    }

    /**
     * Returns a new {@code ImmutableMappingScript}.
     *
     * @param contentType
     * @param mappingEngine
     * @param incomingMappingScript
     * @param outgoingMappingScript
     * @param options
     * @return
     */
    public static ImmutableMappingScript of(final String contentType, final String mappingEngine,
            final String incomingMappingScript, final String outgoingMappingScript, final Map<String, String> options) {
        checkNotNull(contentType, "content-type");
        checkNotNull(mappingEngine, "mapping Engine");
        checkNotNull(incomingMappingScript, "incoming MappingScript");
        checkNotNull(outgoingMappingScript, "outgoing MappingScript");
        checkNotNull(options, "options");

        return new ImmutableMappingScript(contentType, mappingEngine, incomingMappingScript, outgoingMappingScript,
                options);
    }

    /**
     * Creates a new {@code MappingScript} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the MappingScript to be created.
     * @return a new MappingScript which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static MappingScript fromJson(final JsonObject jsonObject) {
        final String contentType = jsonObject.getValueOrThrow(JsonFields.CONTENT_TYPE);
        final String mappingEngine = jsonObject.getValueOrThrow(JsonFields.MAPPING_ENGINE);
        final String incomingMappingScript = jsonObject.getValueOrThrow(JsonFields.INCOMING_MAPPING_SCRIPT);
        final String outgoingMappingScript = jsonObject.getValueOrThrow(JsonFields.OUTGOING_MAPPING_SCRIPT);
        final Map<String, String> options = jsonObject.getValueOrThrow(JsonFields.OPTIONS).stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));

        return of(contentType, mappingEngine, incomingMappingScript, outgoingMappingScript, options);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.CONTENT_TYPE, contentType, predicate);
        jsonObjectBuilder.set(JsonFields.MAPPING_ENGINE, mappingEngine, predicate);
        jsonObjectBuilder.set(JsonFields.INCOMING_MAPPING_SCRIPT, incomingMappingScript, predicate);
        jsonObjectBuilder.set(JsonFields.OUTGOING_MAPPING_SCRIPT, outgoingMappingScript, predicate);
        jsonObjectBuilder.set(JsonFields.OPTIONS, options.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), JsonValue.of(e.getValue())))
                .collect(JsonCollectors.fieldsToObject()), predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getMappingEngine() {
        return mappingEngine;
    }

    @Override
    public String getIncomingMappingScript() {
        return incomingMappingScript;
    }

    @Override
    public String getOutgoingMappingScript() {
        return outgoingMappingScript;
    }

    @Override
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableMappingScript)) {
            return false;
        }
        final ImmutableMappingScript that = (ImmutableMappingScript) o;
        return Objects.equals(contentType, that.contentType) &&
                Objects.equals(mappingEngine, that.mappingEngine) &&
                Objects.equals(incomingMappingScript, that.incomingMappingScript) &&
                Objects.equals(outgoingMappingScript, that.outgoingMappingScript) &&
                Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, mappingEngine, incomingMappingScript, outgoingMappingScript, options);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "contentType=" + contentType +
                ", mappingEngine=" + mappingEngine +
                ", incomingMappingScript=" + incomingMappingScript +
                ", outgoingMappingScript=" + outgoingMappingScript +
                ", options=" + options +
                "]";
    }
}
