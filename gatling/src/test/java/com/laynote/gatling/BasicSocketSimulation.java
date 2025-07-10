package com.laynote.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

            .during(
                    session -> Duration.ofSeconds(
                            ThreadLocalRandom.current().nextLong(900, 1800)
                    ),
                    "noteInteraction"
            ).on(
                    randomSwitch().on(
                            new Choice.WithWeight(80.0,
                                    exec(ws("Send Update to #{noteId}")
                                            .sendText("{\"noteId\": \"#{noteId}\", \"content\": \"Message from soak test...\"}")
                                    )
                            ),
                            new Choice.WithWeight(20.0,
                                    pause(Duration.ofSeconds(15), Duration.ofSeconds(30))
                            )
                    )
            )
            .exec(ws("Close Note: #{noteId}").close());

    ScenarioBuilder scn = scenario("Baseline Soak Test")
            .exec(noteSession, noteSession);

    {
        setUp(
                scn.injectOpen(
                        rampUsers(50).during(Duration.ofMinutes(2)),

                        rampUsers(50).during(Duration.ofMinutes(5)),

                        constantUsersPerSec(4.5 / 60.0).during(Duration.ofMinutes(10))
                )
        ).protocols(httpProtocol);
    }
}