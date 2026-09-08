package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "street_closure")
public class StreetClosure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private String closureRequestId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private OrdenTrabajo workOrder;
    @Column(nullable = false, length = 300)
    private String location;
    @ElementCollection
    @CollectionTable(name = "street_closure_affected_sections", joinColumns = @JoinColumn(name = "street_closure_id"))
    @OrderColumn(name = "section_order")
    @Column(nullable = false, length = 300)
    private List<String> affectedSections = new ArrayList<>();
    @Column(nullable = false)
    private LocalDate requestedFrom;
    @Column(nullable = false)
    private LocalDate requestedTo;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(nullable = false)
    private String status = "PENDIENTE";

    protected StreetClosure() {}
    public StreetClosure(OrdenTrabajo order, String location, List<String> sections,
                         LocalDate from, LocalDate to, String reason) {
        this.closureRequestId = UUID.randomUUID().toString();
        this.workOrder = order;
        this.location = location;
        this.affectedSections = new ArrayList<>(sections);
        this.requestedFrom = from;
        this.requestedTo = to;
        this.reason = reason;
    }
    public Long getId() { return id; }
    public String getClosureRequestId() { return closureRequestId; }
    public OrdenTrabajo getWorkOrder() { return workOrder; }
    public String getLocation() { return location; }
    public List<String> getAffectedSections() { return affectedSections; }
    public LocalDate getRequestedFrom() { return requestedFrom; }
    public LocalDate getRequestedTo() { return requestedTo; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
}
