package com.cafeorbe.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Saldo materializado de un usuario. Solo el ledger lo modifica. */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private long saldo;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Cuenta() {
    }

    public Cuenta(UUID usuarioId) {
        this.usuarioId = usuarioId;
        this.saldo = 0;
        this.actualizadoEn = Instant.now();
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public long getSaldo() {
        return saldo;
    }

    /** Suma un monto (positivo o negativo) y devuelve el saldo resultante. El saldo nunca queda negativo. */
    public long aplicar(long monto) {
        if (saldo + monto < 0) {
            throw new IllegalStateException("El saldo no puede quedar negativo");
        }
        this.saldo += monto;
        this.actualizadoEn = Instant.now();
        return saldo;
    }
}
