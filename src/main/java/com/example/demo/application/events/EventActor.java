package com.example.demo.application.events;

/** Identity is resolved from persisted users, never supplied by an HTTP body. */
public interface EventActor {
    java.util.UUID currentPersonId();
}
