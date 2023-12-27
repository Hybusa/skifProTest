package org.example.endpoints;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.SecurityDirectives;
import org.example.actor.UserRegistry;
import org.example.dto.LoginDto;
import org.example.dto.RegisterDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;


public class UserRoutes {

    private final static Logger log = LoggerFactory.getLogger(UserRoutes.class);
    private final ActorRef<UserRegistry.Command> userRegistryActor;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public UserRoutes(ActorSystem<?> system, ActorRef<UserRegistry.Command> userRegistryActor) {
        this.userRegistryActor = userRegistryActor;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<UserRegistry.GetUserResponse> getUser(String name) {
        return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.GetUser(name, ref), askTimeout, scheduler);
    }

    private CompletionStage<UserRegistry.ActionPerformed> loginUser(LoginDto loginDto) {
        return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.LoginUser(loginDto, ref), askTimeout, scheduler);
    }

    private CompletionStage<UserRegistry.ActionPerformed> createUser(RegisterDto user) {
        return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.CreateUser(user, ref), askTimeout, scheduler);
    }

    private CompletionStage<UserRegistry.ActionPerformed> authenticateUser(String username, String password) {
        return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.AuthenticateUser(username,password, ref), askTimeout, scheduler);
    }

    public Route apiRoutes() {
        return pathPrefix("api_v1", () ->
                concat(
                        path("registrate", () ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(RegisterDto.class),
                                                        registerDto ->
                                                                onSuccess(createUser(registerDto), performed -> {
                                                                    if (performed.isSuccess()) {
                                                                        log.info(String.format("User %s created", registerDto.getEmail()));
                                                                        return complete(StatusCodes.OK, "{}");
                                                                    } else {
                                                                        log.info(String.format("User %s was not created already exists", registerDto.getEmail()));
                                                                        return complete(StatusCodes.UNPROCESSABLE_CONTENT, "{ \"error\": \"session.errors.emailAlreadyRegistered\" }");
                                                                    }
                                                                })
                                                )
                                        )
                                )
                        ),
                        path("login", () ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(LoginDto.class),
                                                        loginDto ->
                                                                onSuccess(loginUser(loginDto), performed -> {
                                                                    if (performed.isSuccess()) {
                                                                        log.info(String.format("User %s created", loginDto.getEmail()));
                                                                        return complete(StatusCodes.OK, "{}");
                                                                    } else {
                                                                        log.info(String.format("User %s not logged in", loginDto.getEmail()));
                                                                        return complete(StatusCodes.UNPROCESSABLE_CONTENT, "{ \"error\": \"session.errors.emailAlreadyRegistered\" }");
                                                                    }
                                                                })
                                                )
                                        )
                                )
                        ), path("me", () ->
                                authenticateBasic("credentialsAuthenticator", this::authenticate, user -> {
                                    
                                })
                        ), path("logout", () ->
                                concat(
                                        put(() -> {
                                                    log.info("User logged out");
                                                    return complete(StatusCodes.OK, "{}");
                                                }
                                        )
                                ))
                ));
    }

    private Optional<Object> authenticate(Optional<SecurityDirectives.ProvidedCredentials> providedCredentials) {
    }


}
