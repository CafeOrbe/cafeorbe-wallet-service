package com.cafeorbe.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila inmutable del libro de movimientos: no tiene setters ni se actualiza después de insertarse. */
@Entity
@Table(name = "movimiento")
public class Movimiento {

    public enum Tipo {
        CARGA_AUTOMATICA,
        COBRO
    }

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private Tipo tipo;

    @Column(nullable = false, updatable = false)
    private long monto;

    @Column(name = "saldo_resultante", nullable = false, updatable = false)
    private long saldoResultante;

    @Column(nullable = false, updatable = false, length = 100)
    private String referencia;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Movimiento() {
    }

    public Movimiento(UUID usuarioId, Tipo tipo, long monto, long saldoResultante, String referencia) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.monto = monto;
        this.saldoResultante = saldoResultante;
        this.referencia = referencia;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public long getMonto() {
        return monto;
    }

    public long getSaldoResultante() {
        return saldoResultante;
    }

    public String getReferencia() {
        return referencia;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
