package com.cafeorbe.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }

    public EventoProcesado(UUID eventId) {
        this.eventId = eventId;
        this.procesadoEn = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }
}
