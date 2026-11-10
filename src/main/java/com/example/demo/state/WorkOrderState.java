package com.example.demo.state;

import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.model.EstadoOT;

/**
 * State pattern for the lifecycle of the central work-order aggregate.
 * Each persisted {@link EstadoOT} resolves to one state object that owns the
 * transitions accepted from that state.
 */
public interface WorkOrderState {

    EstadoOT value();

    default EstadoOT schedule(boolean hasCrew) {
        throw invalid("programar");
    }

    default EstadoOT start() {
        throw invalid("iniciar");
    }

    default EstadoOT pause() {
        throw invalid("pausar");
    }

    default EstadoOT complete() {
        throw invalid("completar");
    }

    default EstadoOT validate(boolean approved) {
        throw invalid("validar");
    }

    private InvalidStateTransitionException invalid(String action) {
        return new InvalidStateTransitionException(
                "No se puede " + action + " una orden de trabajo en estado " + value());
    }
}
