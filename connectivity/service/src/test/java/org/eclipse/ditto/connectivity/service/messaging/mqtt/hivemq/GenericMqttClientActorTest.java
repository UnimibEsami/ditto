/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttServerRule;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttPublishResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.model.Uri;
import akka.testkit.javadsl.TestKit;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Unit test for {@link GenericMqttClientActor}
 */
@RunWith(MockitoJUnitRunner.class)
public final class GenericMqttClientActorTest extends AbstractBaseClientActorTest {

    private static final TestConstants.FreePort FREE_PORT = new TestConstants.FreePort();

    @ClassRule
    public static final MqttServerRule MQTT_SERVER = new MqttServerRule(FREE_PORT.getPort());

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    private static final Uri SERVER_HOST = Uri.create(MessageFormat.format("tcp://localhost:{0}", FREE_PORT));
    private static final String SOURCE_ADDRESS = "source/status";
    private static final Source MQTT_SOURCE = ConnectivityModelFactory.newSourceBuilder()
            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
            .index(1)
            .consumerCount(1)
            .address(SOURCE_ADDRESS)
            .qos(MqttQos.AT_LEAST_ONCE.getCode())
            .build();
    private static final Target MQTT_TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address("target/laserBeamer")
            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
            .qos(MqttQos.AT_LEAST_ONCE.getCode())
            .topics(Topic.TWIN_EVENTS)
            .build();

    private static Supplier<ConnectionType> mqttConnectionTypeSupplier;

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(TestConstants.CONFIG);
    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private GenericMqttClient genericMqttClient;
    @Mock
    private GenericMqttClientFactory genericMqttClientFactory;

    private TestKit proxyActor;
    private TestKit connectionActor;

    @BeforeClass
    public static void beforeClass() {
        final var mqttConnectionTypes = List.of(ConnectionType.MQTT, ConnectionType.MQTT_5);
        final var random = new Random();

        // It should not matter what the exact MQTT protocol version is,
        // because the GenericMqttClientActor is supposed to be - generic.
        mqttConnectionTypeSupplier = () -> mqttConnectionTypes.get(random.nextInt(mqttConnectionTypes.size()));
    }

    @Before
    public void before() {
        proxyActor = actorSystemResource.newTestKit();
        connectionActor = actorSystemResource.newTestKit();

        enableGenericMqttClientMethodStubbing();
        enableGenericMqttClientFactoryMethodStubbing();
    }

    private void enableGenericMqttClientMethodStubbing() {
        Mockito.when(genericMqttClient.connect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(genericMqttClient.connect(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(genericMqttClient.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(genericMqttClient.subscribe(Mockito.any()))
                .thenReturn(Single.just(Mockito.mock(GenericMqttSubAck.class)));
        Mockito.when(genericMqttClient.consumeSubscribedPublishesWithManualAcknowledgement())
                .thenReturn(Flowable.never());
        Mockito.when(genericMqttClient.publish(Mockito.any()))
                .thenAnswer(invocation -> {
                    final GenericMqttPublish genericMqttPublish = invocation.getArgument(0);
                    final var proxyActorRef = proxyActor.getRef();
                    proxyActorRef.tell(genericMqttPublish, ActorRef.noSender());
                    return CompletableFuture.completedFuture(GenericMqttPublishResult.success(genericMqttPublish));
                });
    }

    private void enableGenericMqttClientFactoryMethodStubbing() {
        Mockito.when(genericMqttClientFactory.getProductiveGenericMqttClient(Mockito.any()))
                .thenReturn(genericMqttClient);
        Mockito.when(genericMqttClientFactory.getGenericMqttClientForConnectionTesting(Mockito.any()))
                .thenReturn(genericMqttClient);
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        final var connectionBuilder = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        mqttConnectionTypeSupplier.get(),
                        ConnectivityStatus.OPEN,
                        SERVER_HOST.toString())
                .sources(List.of(MQTT_SOURCE))
                .targets(List.of(MQTT_TARGET))
                .failoverEnabled(false);
        if (isSecure) {
            connectionBuilder.uri(SERVER_HOST.scheme("ssl").toString()).build();
        }
        return connectionBuilder.build();
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return GenericMqttClientActor.props(connection,
                proxyActor,
                connectionActor.getRef(),
                DittoHeaders.empty(),
                ConfigFactory.empty(),
                genericMqttClientFactory);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystemResource.getActorSystem();
    }

    @Test
    public void openAndCloseConnection() {
        final var testKit = actorSystemResource.newTestKit();
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var underTest = testKit.childActorOf(createClientActor(proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .connectionStatus(ConnectivityStatus.CLOSED)
                        .specificConfig(Map.of("separatePublisherClient", "false"))
                        .build()));

        underTest.tell(OpenConnection.of(CONNECTION_ID, dittoHeaders), testKit.getRef());

        testKit.expectMsg(CONNECTED_SUCCESS);

        underTest.tell(CloseConnection.of(CONNECTION_ID, dittoHeaders), testKit.getRef());

        testKit.expectMsg(DISCONNECTED_SUCCESS);
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void testConnectionIsSuccessful() {
        final var testKit = actorSystemResource.newTestKit();
        final var connection = getConnection(false);
        final var underTest =
                testKit.watch(testKit.childActorOf(createClientActor(proxyActor.getRef(), connection)));

        underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

        testKit.expectMsg(new Status.Success("successfully connected + initialized mapper"));
        Mockito.verify(genericMqttClient).disconnect();
        testKit.expectTerminated(Duration.ofSeconds(5L), underTest);
        Mockito.verify(genericMqttClient).disconnect();
    }

    @Test
    public void testConnectionFails() {
        final var mqttClientConnectException = new MqttClientConnectException("Failed to connect.", null);
        Mockito.when(genericMqttClient.connect(Mockito.any())).thenThrow(mqttClientConnectException);
        final var testKit = actorSystemResource.newTestKit();
        final var connection = getConnection(false);
        final var underTest =
                testKit.watch(testKit.childActorOf(createClientActor(proxyActor.getRef(), connection)));

        underTest.tell(TestConnection.of(connection, getDittoHeadersWithCorrelationId()), testKit.getRef());

        final var failure = testKit.expectMsgClass(Status.Failure.class);
        assertThat(failure.cause())
                .isInstanceOfSatisfying(ConnectionFailedException.class, connectionFailedException -> {
                    assertThat(connectionFailedException.getDescription())
                            .hasValue("Cause: " + mqttClientConnectException.getMessage());
                    assertThat(connectionFailedException).hasCause(mqttClientConnectException);
                });
        testKit.expectTerminated(Duration.ofSeconds(5L), underTest);
        Mockito.verify(genericMqttClient, Mockito.never()).disconnect();
    }

    @Test
    public void subscribeFails() {
        final var mqttSubscribeException = new MqttSubscribeException("Quisquam omnis in quia hic et libero.", null);
        Mockito.when(genericMqttClient.subscribe(Mockito.any())).thenReturn(Single.error(mqttSubscribeException));
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .connectionStatus(ConnectivityStatus.CLOSED)
                        .build()
        ));

        underTest.tell(OpenConnection.of(CONNECTION_ID, getDittoHeadersWithCorrelationId()), testKit.getRef());

        assertThat(testKit.expectMsgClass(Duration.ofSeconds(10L), Status.Failure.class))
                .satisfies(failure -> assertThat(failure.cause())
                        .isInstanceOf(ConnectionFailedException.class)
                        .hasCause(mqttSubscribeException));
    }

    @Test
    public void consumeFromTopicAndRetrieveConnectionMetrics() {
        final var testKit = actorSystemResource.newTestKit();
        enableSubscribingAndConsumingMethodStubbing(getMqttPublish(SOURCE_ADDRESS, getSerializedModifyThingCommand()));
        final var underTest = testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .sources(List.of(ConnectivityModelFactory.newSourceBuilder()
                                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                .index(2)
                                .consumerCount(1)
                                .address("topic1")
                                .address("topic2")
                                .qos(1)
                                .build()))
                        .build())
        );
        final var dittoHeadersWithCorrelationId = getDittoHeadersWithCorrelationId();

        testKit.expectNoMessage();
        final var modifyThing = proxyActor.expectMsgClass(ModifyThing.class);
        assertThat(modifyThing.getDittoHeaders())
                .doesNotContainKeys(MqttHeader.getHeaderNames().toArray(String[]::new));

        underTest.tell(RetrieveConnectionMetrics.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsgClass(RetrieveConnectionMetricsResponse.class);
    }

    private String getSerializedModifyThingCommand(final Object... correlationIdSuffixes) {
        final CharSequence correlationId;
        if (0 < correlationIdSuffixes.length) {
            correlationId = testNameCorrelationId.getCorrelationId(
                    correlationIdSuffixes[0].toString(),
                    Arrays.stream(Arrays.copyOfRange(correlationIdSuffixes, 1, correlationIdSuffixes.length))
                            .map(Object::toString)
                            .toArray(String[]::new)
            );
        } else {
            correlationId = testNameCorrelationId.getCorrelationId();
        }
        return TestConstants.modifyThing(correlationId.toString());
    }

    private static GenericMqttPublish getMqttPublish(final String topic, final String payload) {
        return GenericMqttPublish.builder(MqttTopic.of(topic), MqttQos.AT_MOST_ONCE)
                .payload(ByteBufferUtils.fromUtf8String(payload))
                .build();
    }

    private void enableSubscribingAndConsumingMethodStubbing(final GenericMqttPublish... incomingPublishes) {
        Mockito.doAnswer(invocation -> {
                    final GenericMqttSubscribe genericMqttSubscribe = invocation.getArgument(0);

                    final var subscribedMqttTopicFilters = genericMqttSubscribe.genericMqttSubscriptions()
                            .map(GenericMqttSubscription::getMqttTopicFilter)
                            .collect(Collectors.toList());

                    // This needs to be a side effect, unfortunately.
                    Mockito.when(genericMqttClient.consumeSubscribedPublishesWithManualAcknowledgement())
                            .thenReturn(Flowable.fromIterable(
                                    Stream.of(incomingPublishes)
                                            .filter(incoming -> subscribedMqttTopicFilters.stream()
                                                    .anyMatch(topicFilter -> topicFilter.matches(incoming.getTopic())))
                                            .collect(Collectors.toList())
                            ));

                    return Single.just(Mockito.mock(GenericMqttSubAck.class));
                })
                .when(genericMqttClient).subscribe(Mockito.any());
    }

    @Test
    public void consumeFromTopicWithSourceHeaderMapping() {
        enableSubscribingAndConsumingMethodStubbing(getMqttPublish(SOURCE_ADDRESS, getSerializedModifyThingCommand()));
        final var testKit = actorSystemResource.newTestKit();
        final var connection = getConnection(false);
        final var headerMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                MqttHeader.MQTT_TOPIC.getName(), getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName()),
                MqttHeader.MQTT_QOS.getName(), getHeaderPlaceholder(MqttHeader.MQTT_QOS.getName()),
                MqttHeader.MQTT_RETAIN.getName(), getHeaderPlaceholder(MqttHeader.MQTT_RETAIN.getName()),
                "custom.topic", getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName()),
                "custom.qos", getHeaderPlaceholder(MqttHeader.MQTT_QOS.getName()),
                "custom.retain", getHeaderPlaceholder(MqttHeader.MQTT_RETAIN.getName())
        ));
        testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(connection)
                        .setSources(connection.getSources()
                                .stream()
                                .map(ConnectivityModelFactory::newSourceBuilder)
                                .map(sourceBuilder -> sourceBuilder.headerMapping(headerMapping))
                                .map(SourceBuilder::build)
                                .collect(Collectors.toList()))
                        .build())
        );

        final var modifyThing = proxyActor.expectMsgClass(ModifyThing.class);
        assertThat(modifyThing.getDittoHeaders())
                .contains(
                        Map.entry(MqttHeader.MQTT_TOPIC.getName(), SOURCE_ADDRESS),
                        Map.entry(MqttHeader.MQTT_QOS.getName(), "0"),
                        Map.entry(MqttHeader.MQTT_RETAIN.getName(), "false"),
                        Map.entry("custom.topic", SOURCE_ADDRESS),
                        Map.entry("custom.qos", "0"),
                        Map.entry("custom.retain", "false")
                );
    }

    private static String getHeaderPlaceholder(final String headerName) {
        return "{{ header:" + headerName + "}}";
    }

    @Test
    public void consumeFromTopicWithIdEnforcement() {
        enableSubscribingAndConsumingMethodStubbing(getMqttPublish("eclipse/ditto/thing",
                getSerializedModifyThingCommand()));
        final var testKit = actorSystemResource.newTestKit();
        testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .setSources(List.of(ConnectivityModelFactory.newSourceBuilder()
                                .addresses(Set.of("eclipse/+/+"))
                                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                .consumerCount(1)
                                .enforcement(ConnectivityModelFactory.newSourceAddressEnforcement(
                                        "eclipse/{{ thing:namespace }}/{{ thing:name }}"
                                ))
                                .replyTarget(ReplyTarget.newBuilder().address("{{ header:reply-to }}").build())
                                .qos(1)
                                .build()))
                        .build()
        ));

        proxyActor.expectMsgClass(ModifyThing.class);
    }

    @Test
    public void consumeFromTopicWithIdEnforcementExpectErrorResponse() {
        enableSubscribingAndConsumingMethodStubbing(getMqttPublish("eclipse/invalid/address",
                getSerializedModifyThingCommand()));
        final var testKit = actorSystemResource.newTestKit();
        testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .setSources(List.of(ConnectivityModelFactory.newSourceBuilder()
                                .addresses(Set.of("eclipse/+/+"))
                                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                .consumerCount(1)
                                .enforcement(ConnectivityModelFactory.newSourceAddressEnforcement(
                                        "eclipse/{{ thing:namespace }}/{{ thing:name }}"
                                ))
                                .replyTarget(ReplyTarget.newBuilder().address("{{ header:reply-to }}").build())
                                .qos(1)
                                .build()))
                        .build())
        );

        final var genericMqttPublish = proxyActor.expectMsgClass(GenericMqttPublish.class);
        assertThat(genericMqttPublish.getPayloadAsHumanReadable())
                .hasValueSatisfying(payload -> assertThat(payload)
                        .contains(ConnectionSignalIdEnforcementFailedException.ERROR_CODE));
    }

    @Test
    public void consumeMultipleSources() {
        final var topicA1 = "A1";
        final var topicB1 = "B1";
        final var topicB2 = "B2";
        final var topicC1 = "C1";
        final var topicC2 = "C2";
        final var topicC3 = "C3";
        final var relevantTopicsCounts = new LinkedHashMap<String, Long>();
        relevantTopicsCounts.put(topicA1, 3L);
        relevantTopicsCounts.put(topicB1, 2L);
        relevantTopicsCounts.put(topicB2, 2L);
        relevantTopicsCounts.put(topicC1, 1L);
        relevantTopicsCounts.put(topicC2, 1L);
        relevantTopicsCounts.put(topicC3, 1L);
        final var genericMqttPublishesForRelevantTopics = relevantTopicsCounts.entrySet()
                .stream()
                .flatMap(entry -> {
                    final var topic = entry.getKey();
                    return IntStream.range(0, Math.toIntExact(entry.getValue()))
                            .mapToObj(i -> getMqttPublish(topic, getSerializedModifyThingCommand(topic, i)));
                })
                .toList();
        enableSubscribingAndConsumingMethodStubbing(
                Stream.concat(
                        Stream.of("foo", "bar").map(t -> getMqttPublish(t, getSerializedModifyThingCommand(t))),
                        genericMqttPublishesForRelevantTopics.stream()
                ).toArray(GenericMqttPublish[]::new)
        );
        final var testKit = actorSystemResource.newTestKit();
        final var headerMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                MqttHeader.MQTT_TOPIC.getName(), getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName()),
                "custom.topic", getHeaderPlaceholder(MqttHeader.MQTT_TOPIC.getName())
        ));
        testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .sources(List.of(
                                ConnectivityModelFactory.newSourceBuilder()
                                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                        .index(1)
                                        .consumerCount(3)
                                        .addresses(Set.of(topicA1))
                                        .headerMapping(headerMapping)
                                        .qos(1)
                                        .build(),
                                ConnectivityModelFactory.newSourceBuilder()
                                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                        .index(2)
                                        .consumerCount(3)
                                        .addresses(Set.of(topicB1, topicB2))
                                        .headerMapping(headerMapping)
                                        .qos(1)
                                        .build(),
                                ConnectivityModelFactory.newSourceBuilder()
                                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                                        .index(3)
                                        .consumerCount(1)
                                        .addresses(Set.of(topicC1, topicC2, topicC3))
                                        .headerMapping(headerMapping)
                                        .qos(1)
                                        .build()
                        ))
                        .build())
        );

        final var receivedTopicsCounts = IntStream.range(0, genericMqttPublishesForRelevantTopics.size())
                .mapToObj(i -> proxyActor.expectMsgClass(ModifyThing.class))
                .map(ModifyThing::getDittoHeaders)
                .map(dittoHeaders -> dittoHeaders.getOrDefault("custom.topic", "n/a"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertThat(receivedTopicsCounts).containsExactlyInAnyOrderEntriesOf(relevantTopicsCounts);
    }

    @Test
    public void reconnectAndConsumeFromTopic() {
        enableSubscribingAndConsumingMethodStubbing(getMqttPublish(SOURCE_ADDRESS, getSerializedModifyThingCommand()));
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.childActorOf(createClientActor(proxyActor.getRef(), getConnection(false)));
        final var dittoHeadersWithCorrelationId = getDittoHeadersWithCorrelationId();

        proxyActor.expectMsgClass(ModifyThing.class);

        underTest.tell(CloseConnection.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsg(DISCONNECTED_SUCCESS);

        underTest.tell(OpenConnection.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsg(CONNECTED_SUCCESS);

        // ModifyThing automatically published by mock connection.
        proxyActor.expectMsgClass(ModifyThing.class);

        underTest.tell(CloseConnection.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsg(DISCONNECTED_SUCCESS);
    }

    @Test
    public void publishToTopic() {
        final var testKit = actorSystemResource.newTestKit();
        final var dittoHeadersWithCorrelationId = getDittoHeadersWithCorrelationId();
        final var authorizationContext = MQTT_TARGET.getAuthorizationContext();
        final var thingModifiedEvent = TestConstants.thingModified(authorizationContext.getAuthorizationSubjects());
        final var underTest = testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .connectionStatus(ConnectivityStatus.CLOSED)
                        .build()
        ));

        underTest.tell(OpenConnection.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsg(CONNECTED_SUCCESS);

        underTest.tell(thingModifiedEvent, testKit.getRef());

        assertThat(proxyActor.expectMsgClass(GenericMqttPublish.class))
                .satisfies(genericMqttPublish -> {
                    assertThat(genericMqttPublish.getTopic()).isEqualTo(MqttTopic.of(MQTT_TARGET.getAddress()));
                    assertThat(genericMqttPublish.getPayload())
                            .hasValue(ByteBufferUtils.fromUtf8String(
                                    TestConstants.signalToDittoProtocolJsonString(thingModifiedEvent)
                            ));
                });
    }

    @Test
    public void publishToReplyTarget() {
        final var testKit = actorSystemResource.newTestKit();
        final var dittoHeadersWithCorrelationId = getDittoHeadersWithCorrelationId();
        final var thingId = ThingId.generateRandom();
        final var underTest = testKit.childActorOf(createClientActor(
                proxyActor.getRef(),
                ConnectivityModelFactory.newConnectionBuilder(getConnection(false))
                        .connectionStatus(ConnectivityStatus.CLOSED)
                        .setSources(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT)
                        .build()
        ));

        underTest.tell(OpenConnection.of(CONNECTION_ID, dittoHeadersWithCorrelationId), testKit.getRef());

        testKit.expectMsg(CONNECTED_SUCCESS);

        underTest.tell(
                DeleteThingResponse.of(
                        thingId,
                        DittoHeaders.newBuilder(dittoHeadersWithCorrelationId)
                                .replyTarget(0)
                                .expectedResponseTypes(ResponseType.values())
                                .build()
                ),
                testKit.getRef()
        );

        assertThat(proxyActor.expectMsgClass(GenericMqttPublish.class))
                .satisfies(genericMqttPublish ->
                        assertThat(genericMqttPublish.getTopic()).isEqualTo(MqttTopic.of("replyTarget/" + thingId)));
    }

}
