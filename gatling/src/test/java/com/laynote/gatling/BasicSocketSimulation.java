package com.laynote.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

public class BasicSocketSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = HttpDsl.http
            .baseUrl("http://localhost:8080")
            .wsBaseUrl("ws://localhost:8080");

    ScenarioBuilder scn = CoreDsl.scenario("WebSocket Ten Minute Test")
            .exec(
                    HttpDsl.ws("Connect to /notes").connect("/notes")
            )
            .repeat(60, "counter").on(
                    CoreDsl.exec(
                            HttpDsl.ws("Send Message").sendText("Update message #{counter} from Gatling!")
                    )
                            .pause(Duration.ofSeconds(10))
            )
            .exec(
                    HttpDsl.ws("Close Connection").close()
            );

    {
        setUp(
                scn.injectOpen(
                        CoreDsl.atOnceUsers(100)
                )
        ).protocols(httpProtocol);
    }
} 