/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocoladapter.adaptables;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;

/**
 * Abstract base class for implementations of {@link MappingStrategies}. It implements the {@link #find(String)}
 * functionality and provides methods to map from {@link Adaptable}s to {@link org.eclipse.ditto.signals.base.Signal}s
 * that are common to all types.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        implements MappingStrategies<T> {

    private final Map<String, JsonifiableMapper<T>> mappingStrategies;

    protected AbstractMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        this.mappingStrategies = checkNotNull(mappingStrategies, "mappingStrategies");
    }

    @Nullable
    @Override
    public JsonifiableMapper<T> find(final String type) {
        return mappingStrategies.get(type);
    }

    /**
     * Reads Ditto headers from an Adaptable. CAUTION: Headers are taken as-is!.
     *
     * @param adaptable the protocol message.
     * @return the headers of the message.
     */
    protected static DittoHeaders dittoHeadersFrom(final Adaptable adaptable) {
        return adaptable.getHeaders().orElseGet(DittoHeaders::empty);
    }

    /**
     * Checks if the given {@link Adaptable} is a {@link HttpStatusCode#CREATED} response.
     *
     * @param adaptable an {@link Adaptable}
     * @return {@code true} if the given {@code adaptable} is a response (has a status field) and the status code is
     * {@link HttpStatusCode#CREATED}.
     */
    protected static boolean isCreated(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus()
                .map(HttpStatusCode.CREATED::equals)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static ThingId thingIdFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getId());
    }

}
