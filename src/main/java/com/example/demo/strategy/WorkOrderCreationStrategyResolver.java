package com.example.demo.strategy;

import com.example.demo.exception.InvalidRequestException;
import com.example.demo.model.OrigenOT;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class WorkOrderCreationStrategyResolver {
    private final List<WorkOrderCreationStrategy> strategies;

    public WorkOrderCreationStrategyResolver(List<WorkOrderCreationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
        for (var origin : OrigenOT.values()) {
            if (strategies.stream().filter(strategy -> strategy.supports(origin)).count() != 1) {
                throw new IllegalStateException("Debe existir exactamente una estrategia para " + origin);
            }
        }
    }

    public WorkOrderCreationStrategy resolve(String value) {
        final OrigenOT origin;
        try {
            origin = OrigenOT.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("origin invalido: " + value);
        }
        return strategies.stream().filter(strategy -> strategy.supports(origin)).findFirst().orElseThrow();
    }
}
