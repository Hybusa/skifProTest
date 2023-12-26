package org.example.model;

import akka.http.javadsl.model.DateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class User {
    String id;
    String email;
    String password;
    DateTime created;
    String name;
}
