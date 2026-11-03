package com.example.demo;

import com.example.demo.model.Cuadrilla;
import com.example.demo.model.EstadoActividad;
import com.example.demo.model.EstadoAprobacion;
import com.example.demo.model.EstadoOT;
import com.example.demo.model.Evidencia;
import com.example.demo.model.Maquinaria;
import com.example.demo.model.MaquinariaAsignada;
import com.example.demo.model.Material;
import com.example.demo.model.MaterialAsignado;
import com.example.demo.model.Observacion;
import com.example.demo.model.OrdenTrabajo;
import com.example.demo.model.OrigenOT;
import com.example.demo.model.PrioridadOT;
import com.example.demo.model.ProyectoObra;
import com.example.demo.model.StreetClosure;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCoverageTests {

    @Test
    void resourceEntitiesExposeAssignedValues() {
        Cuadrilla crew = new Cuadrilla("Cuadrilla Norte");
        OrdenTrabajo order = new OrdenTrabajo("request-1", OrigenOT.MANUAL, "Reparar vereda",
                "VEREDA", "Av. Lima 100", PrioridadOT.ALTA, 8, crew);
        Material material = new Material("Cemento", "kg");
        MaterialAsignado assignedMaterial = new MaterialAsignado(order, material, new BigDecimal("25.50"));
        assignedMaterial.registrarConsumo(new BigDecimal("20.00"));
        Maquinaria machine = new Maquinaria("Retroexcavadora");
        MaquinariaAsignada assignedMachine = new MaquinariaAsignada(order, machine, 4);
        Evidencia evidence = new Evidencia(order, "blob://evidence/1", "Trabajo terminado");
        Observacion observation = new Observacion(order, "Control visual aprobado");

        assertThat(crew.getNombre()).isEqualTo("Cuadrilla Norte");
        assertThat(material.getNombre()).isEqualTo("Cemento");
        assertThat(material.getUnidad()).isEqualTo("kg");
        assertThat(assignedMaterial.getOrdenTrabajo()).isSameAs(order);
        assertThat(assignedMaterial.getMaterial()).isSameAs(material);
        assertThat(assignedMaterial.getCantidadRequerida()).isEqualByComparingTo("25.50");
        assertThat(assignedMaterial.getCantidadConsumida()).isEqualByComparingTo("20.00");
        assertThat(machine.getNombre()).isEqualTo("Retroexcavadora");
        assertThat(assignedMachine.getOrdenTrabajo()).isSameAs(order);
        assertThat(assignedMachine.getMaquinaria()).isSameAs(machine);
        assertThat(assignedMachine.getHorasAsignadas()).isEqualTo(4);
        assertThat(evidence.getOrdenTrabajo()).isSameAs(order);
        assertThat(evidence.getStorageReference()).isEqualTo("blob://evidence/1");
        assertThat(evidence.getDescripcion()).isEqualTo("Trabajo terminado");
        assertThat(evidence.getFecha()).isNotNull();
        assertThat(observation.getOrdenTrabajo()).isSameAs(order);
        assertThat(observation.getTexto()).isEqualTo("Control visual aprobado");
        assertThat(observation.getFecha()).isNotNull();
    }

    @Test
    void workOrderKeepsStateCoherentAcrossSchedulingAndUpdates() {
        Cuadrilla crew = new Cuadrilla("Cuadrilla Sur");
        OrdenTrabajo order = new OrdenTrabajo(null, OrigenOT.MANUAL, "Bache", "ASFALTO",
                "Moreno 2200", PrioridadOT.MEDIA, 6, null);

        assertThat(order.getStatus()).isEqualTo(EstadoOT.PENDIENTE);
        order.programar(LocalDate.now().plusDays(1), null);
        assertThat(order.getStatus()).isEqualTo(EstadoOT.PROGRAMADA);
        order.updateDatosGenerales("request-2", OrigenOT.AMBIENTE, "Bache profundo", "ASFALTO",
                "Moreno 2200", PrioridadOT.ALTA, 8, crew);
        assertThat(order.getStatus()).isEqualTo(EstadoOT.ASIGNADA);
        order.iniciar();
        order.updateDatosGenerales("request-2", OrigenOT.AMBIENTE, "Bache profundo", "ASFALTO",
                "Moreno 2200", PrioridadOT.ALTA, 8, null);
        assertThat(order.getStatus()).isEqualTo(EstadoOT.EN_EJECUCION);
        order.pausar();
        order.completar("Reparación finalizada");
        assertThat(order.getOutcome()).isEqualTo("Reparación finalizada");
        order.validar(false);
        assertThat(order.getStatus()).isEqualTo(EstadoOT.REABIERTA);
        order.validar(true);
        assertThat(order.getStatus()).isEqualTo(EstadoOT.VALIDADA);
        assertThat(order.getIntegrationId()).isNotNull();
    }

    @Test
    void projectCoversOptionalUpdatesApprovalAndCaseFileConflict() {
        ProyectoObra project = new ProyectoObra("Plaza", "Refacción", "Integral", "Centro",
                new BigDecimal("1000"), null, LocalDate.now(), 30, null, "Ing. Uno", "Contratista");
        project.updateDatosGenerales("Plaza Central", "Nueva descripción", "Integral", "Centro",
                new BigDecimal("1200"), new BigDecimal("1100"), null, LocalDate.now(), 35,
                32, null, "Ing. Dos", "Contratista Dos");
        assertThat(project.getUsedBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(project.getPhysicalProgress()).isZero();
        project.updateDatosGenerales("Plaza Central", "Nueva descripción", "Integral", "Centro",
                new BigDecimal("1200"), new BigDecimal("1100"), new BigDecimal("200"), LocalDate.now(), 35,
                32, 25, "Ing. Dos", "Contratista Dos");
        project.marcarPendienteAprobacion();
        assertThat(project.getApprovalStatus()).isEqualTo(EstadoAprobacion.PENDIENTE_APROBACION);
        project.aprobar(new BigDecimal("1100"), 32, LocalDate.now(), "Aprobado");
        assertThat(project.getActivityStatus()).isEqualTo(EstadoActividad.SIN_INICIAR);
        project.finalizar();
        assertThat(project.getActivityStatus()).isEqualTo(EstadoActividad.FINALIZADA);
        UUID caseFileId = UUID.randomUUID();
        OffsetDateTime resolvedAt = OffsetDateTime.now();
        project.registrarResolucionExpediente(caseFileId, "EXP-1", "approved", resolvedAt);
        project.registrarResolucionExpediente(caseFileId, "EXP-1", "approved", resolvedAt);
        assertThat(project.getCaseFileId()).isEqualTo(caseFileId);
        assertThatThrownBy(() -> project.registrarResolucionExpediente(UUID.randomUUID(), "EXP-2", "approved", resolvedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROJECT_CASE_FILE_CONFLICT");
        project.rechazar();
        assertThat(project.getApprovalStatus()).isEqualTo(EstadoAprobacion.RECHAZADO);
        project.aprobar();
        assertThat(project.getApprovalStatus()).isEqualTo(EstadoAprobacion.APROBADO);
    }

    @Test
    void streetClosureDerivesStatusFromAuthorizationWindow() {
        OrdenTrabajo order = new OrdenTrabajo(null, OrigenOT.MANUAL, "Corte", "CALLE",
                "Rivadavia", PrioridadOT.ALTA, 4, null);
        StreetClosure closure = new StreetClosure(order, "Rivadavia",
                List.of("Rivadavia entre A y B"), LocalDate.now(), LocalDate.now().plusDays(1), "Obra");
        assertThat(closure.getStatus()).isEqualTo("PENDIENTE");
        assertThat(closure.getClosureRequestId()).isNotBlank();
        assertThat(closure.getAffectedSections()).containsExactly("Rivadavia entre A y B");

        ReflectionTestUtils.setField(closure, "integrationStatus", "APPROVED");
        ReflectionTestUtils.setField(closure, "authorizedStart", OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        ReflectionTestUtils.setField(closure, "authorizedEnd", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2));
        assertThat(closure.getStatus()).isEqualTo("APPROVED");
        ReflectionTestUtils.setField(closure, "authorizedStart", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        assertThat(closure.getStatus()).isEqualTo("ACTIVE");
        ReflectionTestUtils.setField(closure, "authorizedEnd", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(closure.getStatus()).isEqualTo("EXPIRED");
        ReflectionTestUtils.setField(closure, "integrationStatus", "REJECTED");
        assertThat(closure.getStatus()).isEqualTo("REJECTED");
    }
}
