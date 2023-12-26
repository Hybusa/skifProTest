package org.example.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import org.example.dto.LoginDto;
import org.example.dto.RegisterDto;
import org.example.model.User;

import java.util.*;

//#user-registry-actor
public class UserRegistry extends AbstractBehavior<UserRegistry.Command> {

    // actor protocol
    sealed public interface Command permits ActionPerformed, CreateUser, GetUser, LoginUser {
    }

    public final static record CreateUser(RegisterDto registerDto, ActorRef<ActionPerformed> replyTo) implements Command {
    }

    public final static record LoginUser(LoginDto loginDto, ActorRef<ActionPerformed> replyTo) implements Command {
    }

    public final static record GetUserResponse(Optional<User> userOptional) {
    }

    public final static record GetUser(String name, ActorRef<GetUserResponse> replyTo) implements Command {
    }

    public final static record ActionPerformed() implements Command {
    }

    //#user-case-classes

    private final List<User> users = new ArrayList<>();

    private UserRegistry(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(UserRegistry::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(CreateUser.class, this::onCreateUser)
                .onMessage(LoginUser.class, this::onLoginUser)
                .onMessage(GetUser.class, this::onGetUser)

                .build();
    }


    private Behavior<Command> onCreateUser(CreateUser command) {
        if(users.stream().anyMatch(u -> u.getEmail().equals(command.registerDto.getEmail()))) {

        }
        users.add(
                new User(
                        generateID(command.registerDto),
                        command.registerDto.getEmail(),
                        command.registerDto.getPassword(),
                        DateTime.now(),
                        command.registerDto.getName()
                )
        );
        command.replyTo.tell(new ActionPerformed());
        return this;
    }
    private Behavior<Command> onLoginUser(LoginUser command) {
        Optional<User> userOptional = users.stream()
                .filter(user -> user.getName().equals(command.loginDto.getEmail()))
                .findFirst();
        command.replyTo().tell(new ActionPerformed());
        return this;
    }


    private Behavior<Command> onGetUser(GetUser command) {
        Optional<User> maybeUser = users.stream()
                .filter(user -> user.getName().equals(command.name()))
                .findFirst();
        command.replyTo().tell(new GetUserResponse(maybeUser));
        return this;
    }

    private String generateID(Object obj) {
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString();
        return id;
    }


}
