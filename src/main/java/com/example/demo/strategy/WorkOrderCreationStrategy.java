package com.example.demo.strategy;

import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.model.Cuadrilla;
import com.example.demo.model.OrdenTrabajo;
import com.example.demo.model.OrigenOT;

/** Prepara una orden validada, sin persistirla. El Service controla la transaccion. */
public interface WorkOrderCreationStrategy {
    boolean supports(OrigenOT origin);
    OrdenTrabajo create(OrdenTrabajoRequest request, Cuadrilla crew);
}
