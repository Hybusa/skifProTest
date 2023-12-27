package org.example.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.example.dto.LoginDto;
import org.example.dto.RegisterDto;
import org.example.model.User;

import java.time.Instant;
import java.util.*;

public class UserRegistry extends AbstractBehavior<UserRegistry.Command> {

    // actor protocol
    sealed public interface Command permits ActionPerformed, CreateUser, GetUser, LoginUser, AuthenticateUser {
    }

    public record CreateUser(RegisterDto registerDto,
                             ActorRef<ActionPerformed> replyTo) implements Command {
    }

    public record LoginUser(LoginDto loginDto, ActorRef<ActionPerformed> replyTo) implements Command {
    }

    public record AuthenticateUser(String username, String password,
                                   ActorRef<ActionPerformed> replyTo) implements Command {
    }

    public record GetUserResponse(Optional<User> userOptional) {
    }

    public record GetUser(String name, ActorRef<GetUserResponse> replyTo) implements Command {
    }

    public record ActionPerformed(Boolean check) implements Command {
        public boolean isSuccess() {
            return check;
        }
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
                .onMessage(AuthenticateUser.class, this::authenticateUser)
                .build();
    }

    private Behavior<Command> authenticateUser(AuthenticateUser command) {
        Optional<User> userOptional = users.stream().filter(u -> u.getEmail().equals(command.username)).findFirst();
        if (userOptional.isEmpty()) {
            command.replyTo.tell(new ActionPerformed(false));
            return this;
        }
        if (!userOptional.get().getPassword().equals(command.password)) {
            command.replyTo.tell(new ActionPerformed(false));
            return this;
        }
        command.replyTo.tell(new ActionPerformed(true));
        return this;
    }


    private Behavior<Command> onCreateUser(CreateUser command) {
        if (users.stream().anyMatch(u -> u.getEmail().equals(command.registerDto.getEmail()))) {
            command.replyTo.tell(new ActionPerformed(false));
            return this;
        }
        users.add(
                new User(
                        generateID(command.registerDto),
                        command.registerDto.getEmail(),
                        command.registerDto.getPassword(),
                        Date.from(Instant.now()),
                        command.registerDto.getName()
                )
        );
        command.replyTo.tell(new ActionPerformed(true));
        return this;
    }

    private Behavior<Command> onLoginUser(LoginUser command) {
        Optional<User> userOptional = users.stream().filter(u -> u.getEmail().equals(command.loginDto.getEmail())).findFirst();
        if (userOptional.isEmpty()) {
            command.replyTo.tell(new ActionPerformed(false));
            return this;
        }
        if (!userOptional.get().getPassword().equals(command.loginDto.getPassword())) {
            command.replyTo.tell(new ActionPerformed(false));
            return this;
        }
        command.replyTo.tell(new ActionPerformed(true));
        return this;
    }

    private Behavior<Command> onGetUser(GetUser command) {
        Optional<User> maybeUser = users.stream()
                .filter(user -> user.getEmail().equals(command.name()))
                .findFirst();
        command.replyTo().tell(new GetUserResponse(maybeUser));
        return this;
    }

    private String generateID(Object obj) {
        return UUID.nameUUIDFromBytes(obj.toString().getBytes()).toString();
    }
}
