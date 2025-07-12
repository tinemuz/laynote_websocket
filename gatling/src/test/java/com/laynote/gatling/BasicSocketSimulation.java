package com.laynote.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class BasicSocketSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("https://laynote-websocket.fly.dev")
            .wsBaseUrl("wss://laynote-websocket.fly.dev");

    Iterator<Map<String, Object>> noteFeeder =
            Stream.generate((Supplier<Map<String, Object>>) () ->
                    Collections.singletonMap("noteId", UUID.randomUUID().toString())
            ).iterator();

    ChainBuilder noteSession = feed(noteFeeder)
            .exec(ws("Connect to /notes").connect("/notes")
                    .onConnected(
                            exec(ws("Open Note: #{noteId}")
                                    .sendText("{\"action\": \"open\", \"noteId\": \"#{noteId}\"}"))
                    )
            )
            .during(Duration.ofMinutes(8), "noteInteraction").on(
                    randomSwitch().on(
                            new Choice.WithWeight(85.0,
                                    exec(ws("Send Update to #{noteId}")
                                            .sendText("{\"action\": \"update\", \"noteId\": \"#{noteId}\", \"content\": \"Message from Gatling test...\"}"))
                            ),
                            new Choice.WithWeight(15.0,
                                    pause(Duration.ofSeconds(2), Duration.ofSeconds(5))
                            )
                    )
            )
            .exec(ws("Close Note: #{noteId}").close());

    ScenarioBuilder scn = scenario("WebSocket 10-Minute Load Test")
            .exec(noteSession);

    {
        setUp(
                scn.injectOpen(
                        rampUsers(50).during(Duration.ofMinutes(2)),
                        nothingFor(Duration.ofSeconds(5)),
                        constantUsersPerSec(5).during(Duration.ofMinutes(8))
                )
        ).protocols(httpProtocol);
    }
}