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
package org.eclipse.ditto.signals.commands.amqpbridge.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.MappingScript;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;

/**
 * Response to a {@link RetrieveConnection} command.
 */
@Immutable
public final class RetrieveConnectionResponse extends AbstractCommandResponse<RetrieveConnectionResponse>
        implements AmqpBridgeQueryCommandResponse<RetrieveConnectionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveConnection.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_MAPPING_SCRIPTS =
            JsonFactory.newJsonArrayFieldDefinition("mappingScripts", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final AmqpConnection amqpConnection;
    private final List<MappingScript> mappingScripts;

    private RetrieveConnectionResponse(final AmqpConnection amqpConnection, final List<MappingScript> mappingScripts,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.amqpConnection = amqpConnection;
        this.mappingScripts = Collections.unmodifiableList(new ArrayList<>(mappingScripts));
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionResponse}.
     *
     * @param amqpConnection the retrieved connection.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionResponse of(final AmqpConnection amqpConnection, final DittoHeaders dittoHeaders) {
        return of(amqpConnection, Collections.emptyList(), dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionResponse}.
     *
     * @param amqpConnection the retrieved connection.
     * @param mappingScripts the retrieved mapping scripts to apply for different content-types.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionResponse response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionResponse of(final AmqpConnection amqpConnection,
            final List<MappingScript> mappingScripts, final DittoHeaders dittoHeaders) {
        checkNotNull(amqpConnection, "Connection");
        checkNotNull(mappingScripts, "mapping Scripts");
        return new RetrieveConnectionResponse(amqpConnection, mappingScripts, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be retrieved.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveConnectionResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final JsonObject jsonConnection = jsonObject.getValueOrThrow(JSON_CONNECTION);
                    final AmqpConnection readAmqpConnection = AmqpBridgeModelFactory.connectionFromJson(jsonConnection);

                    final JsonArray mappingScripts = jsonObject.getValueOrThrow(JSON_MAPPING_SCRIPTS);
                    final List<MappingScript> readMappingScripts = mappingScripts.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(AmqpBridgeModelFactory::mappingScriptFromJson)
                            .collect(Collectors.toList());

                    return of(readAmqpConnection, readMappingScripts, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONNECTION, amqpConnection.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(JSON_MAPPING_SCRIPTS, mappingScripts.stream()
                .map(ms -> ms.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    /**
     * @return the {@code AmqpConnection}.
     */
    public AmqpConnection getAmqpConnection() {
        return amqpConnection;
    }

    /**
     * @return
     */
    public List<MappingScript> getMappingScripts() {
        return mappingScripts;
    }

    @Override
    public String getConnectionId() {
        return amqpConnection.getId();
    }

    @Override
    public WithEntity setEntity(final JsonValue entity) {
        return fromJson(entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return amqpConnection.toJson(schemaVersion);
    }

    @Override
    public RetrieveConnectionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(amqpConnection, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        final RetrieveConnectionResponse that = (RetrieveConnectionResponse) o;
        return Objects.equals(amqpConnection, that.amqpConnection) &&
                Objects.equals(mappingScripts, that.mappingScripts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), amqpConnection, mappingScripts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", amqpConnection=" + amqpConnection +
                ", mappingScripts=" + mappingScripts +
                "]";
    }
}
