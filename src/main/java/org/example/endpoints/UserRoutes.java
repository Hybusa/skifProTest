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
import org.example.dto.myError;
import org.example.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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
                                                                        return complete(StatusCodes.OK, new HashMap<>(),Jackson.marshaller());
                                                                    } else {
                                                                        log.info(String.format("User %s was not created already exists", registerDto.getEmail()));
                                                                        return complete(StatusCodes.UNPROCESSABLE_CONTENT, new myError("session.errors.emailAlreadyRegistered"),Jackson.marshaller());
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
                                                                        log.info(String.format("User %s logged in", loginDto.getEmail()));
                                                                        return complete(StatusCodes.OK, new HashMap<>(),Jackson.marshaller());
                                                                    } else {
                                                                        log.info(String.format("User %s not logged in", loginDto.getEmail()));
                                                                        return complete(StatusCodes.UNPROCESSABLE_CONTENT, new myError("session.errors"),Jackson.marshaller());
                                                                    }
                                                                })
                                                )
                                        )
                                )
                        ),      path("me", () ->
                                authenticateBasic("Authenticated", this::myAuthenticator, user ->
                                        complete(StatusCodes.OK, user,Jackson.marshaller()))
                        ), path("logout", () ->
                                concat(
                                        put(() -> {
                                                    log.info("User logged out");
                                                    return complete(StatusCodes.OK, new HashMap<>(), Jackson.marshaller());
                                                }
                                        )
                                ))
                ));
    }

    private Optional<Object> myAuthenticator(Optional<SecurityDirectives.ProvidedCredentials> providedCredentialsOptional) {
        if(providedCredentialsOptional.isEmpty()){
            return Optional.empty();
        }
        CompletionStage<UserRegistry.GetUserResponse> userCompletionStage  = getUser(providedCredentialsOptional.get().identifier());

        UserRegistry.GetUserResponse response;
        try {
            response = userCompletionStage.toCompletableFuture().get();
        } catch (InterruptedException |ExecutionException e) {
            throw new RuntimeException(e);
        }
        if(response.userOptional().isEmpty()){
            return Optional.empty();
        }

        User user = response.userOptional().get();

        if(!providedCredentialsOptional.get().verify(user.getPassword())){
            return Optional.empty();
        }


        return Optional.of(user);
    }


}
