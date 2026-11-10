package com.example.demo.state;

import com.example.demo.model.EstadoOT;

import java.util.EnumMap;
import java.util.Map;

/** Registry of immutable State objects. */
public final class WorkOrderStates {
    private static final Map<EstadoOT, WorkOrderState> STATES = build();

    private WorkOrderStates() {
    }

    public static WorkOrderState from(EstadoOT value) {
        WorkOrderState state = STATES.get(value);
        if (state == null) {
            throw new IllegalStateException("Estado de orden no soportado: " + value);
        }
        return state;
    }

    private static Map<EstadoOT, WorkOrderState> build() {
        EnumMap<EstadoOT, WorkOrderState> states = new EnumMap<>(EstadoOT.class);
        states.put(EstadoOT.PENDIENTE, schedulable(EstadoOT.PENDIENTE));
        states.put(EstadoOT.PROGRAMADA, schedulableAndStartable(EstadoOT.PROGRAMADA));
        states.put(EstadoOT.ASIGNADA, schedulableAndStartable(EstadoOT.ASIGNADA));
        states.put(EstadoOT.EN_EJECUCION, new BasicState(EstadoOT.EN_EJECUCION) {
            @Override public EstadoOT pause() { return EstadoOT.PAUSADA; }
            @Override public EstadoOT complete() { return EstadoOT.COMPLETADA; }
        });
        states.put(EstadoOT.PAUSADA, new BasicState(EstadoOT.PAUSADA) {
            @Override public EstadoOT start() { return EstadoOT.EN_EJECUCION; }
            @Override public EstadoOT complete() { return EstadoOT.COMPLETADA; }
        });
        states.put(EstadoOT.COMPLETADA, new BasicState(EstadoOT.COMPLETADA) {
            @Override public EstadoOT validate(boolean approved) {
                return approved ? EstadoOT.VALIDADA : EstadoOT.REABIERTA;
            }
        });
        states.put(EstadoOT.VALIDADA, new BasicState(EstadoOT.VALIDADA));
        states.put(EstadoOT.REABIERTA, new BasicState(EstadoOT.REABIERTA) {
            @Override public EstadoOT start() { return EstadoOT.EN_EJECUCION; }
            @Override public EstadoOT validate(boolean approved) {
                return approved ? EstadoOT.VALIDADA : EstadoOT.REABIERTA;
            }
        });
        return Map.copyOf(states);
    }

    private static WorkOrderState schedulable(EstadoOT value) {
        return new BasicState(value) {
            @Override public EstadoOT schedule(boolean hasCrew) {
                return hasCrew ? EstadoOT.ASIGNADA : EstadoOT.PROGRAMADA;
            }
        };
    }

    private static WorkOrderState schedulableAndStartable(EstadoOT value) {
        return new BasicState(value) {
            @Override public EstadoOT schedule(boolean hasCrew) {
                return hasCrew ? EstadoOT.ASIGNADA : EstadoOT.PROGRAMADA;
            }
            @Override public EstadoOT start() { return EstadoOT.EN_EJECUCION; }
            @Override public EstadoOT complete() { return EstadoOT.COMPLETADA; }
        };
    }

    private static class BasicState implements WorkOrderState {
        private final EstadoOT value;

        private BasicState(EstadoOT value) {
            this.value = value;
        }

        @Override public EstadoOT value() { return value; }
    }
}
