package com.laynote.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class BasicSocketSimulation extends Simulation {

    private static String getProperty(String propertyName, String defaultValue) {
        return Optional.ofNullable(System.getProperty(propertyName)).orElse(defaultValue);
    }

    private static final String BASE_URL = getProperty("baseUrl", "https://laynote-websocket.fly.dev");
    private static final String WS_BASE_URL = getProperty("wsBaseUrl", "wss://laynote-websocket.fly.dev");

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .wsBaseUrl(WS_BASE_URL);

    ChainBuilder multiNoteUserJourney =
            exec(ws("Connect to /notes").connect("/notes"))
                    .exec(session -> session.set("noteIds", new ArrayList<String>()))

                    .repeat(3, "creationCounter").on(
                            exec(ws("Create Note #{creationCounter}")
                                    .sendText("{\"action\": \"CREATE_NOTE\", \"userId\": 4, \"title\": \"Gatling Note #{creationCounter}\"}")
                                    .await(30).on(
                                            ws.checkTextMessage("Check for NOTE_CREATED")
                                                    .check(jsonPath("$.noteId").saveAs("tempNoteId"))
                                    )
                            )
                                    .exec(session -> {
                                        String newNoteId = session.getString("tempNoteId");
                                        List<String> noteIds = session.getList("noteIds");
                                        noteIds.add(newNoteId);
                                        return session.set("noteIds", noteIds);
                                    })
                    )

                    .during(Duration.ofMinutes(5), "interactionLoop").on(
                            exec(session -> {
                                List<String> noteIds = session.getList("noteIds");
                                if (noteIds.isEmpty()) {
                                    return session;
                                }
                                int randomIndex = ThreadLocalRandom.current().nextInt(noteIds.size());
                                String randomNoteId = noteIds.get(randomIndex);
                                return session.set("currentNoteId", randomNoteId);
                            })
                                    .exec(ws("Load Note: #{currentNoteId}")
                                            .sendText("{\"action\": \"LOAD_NOTE\", \"noteId\": \"#{currentNoteId}\"}")
                                    )
                                    .pause(1)
                                    .exec(ws("Update Note: #{currentNoteId}")
                                            .sendText("{\"action\": \"UPDATE_CONTENT\", \"noteId\": \"#{currentNoteId}\", \"content\": \"User 4 is updating one of their several notes...\"}")
                                    )
                                    .pause(Duration.ofSeconds(5), Duration.ofSeconds(15))
                    )

                    .exec(ws("Close Connection").close());


    ScenarioBuilder scn = scenario("Multi-Note Update Simulation")
            .exec(multiNoteUserJourney);

    {
        // ramp up 10 users and then continuously loop through the journey for 5 minutes
        // 30 notes created
        // with each updated repeatedly
        setUp(
                scn.injectOpen(
                        rampUsers(10).during(Duration.ofMinutes(1))
                ).throttle(
                        reachRps(10).in(Duration.ofSeconds(10)),
                        holdFor(Duration.ofMinutes(5))
                )
        ).protocols(httpProtocol);
    }
}