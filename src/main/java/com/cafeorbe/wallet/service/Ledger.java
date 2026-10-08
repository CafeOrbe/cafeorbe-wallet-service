package com.cafeorbe.wallet.service;

import com.cafeorbe.wallet.model.Cuenta;
import com.cafeorbe.wallet.model.Movimiento;
import com.cafeorbe.wallet.repository.CuentaRepository;
import com.cafeorbe.wallet.repository.MovimientoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Libro de movimientos de Orbes. El saldo no se edita a mano: cada operación inserta un
 * movimiento inmutable y actualiza el saldo materializado en la misma transacción.
 */
@Service
public class Ledger {

    /** Referencia fija: la carga automática ocurre una sola vez por usuario. */
    static final String REFERENCIA_CARGA_INICIAL = "saldo-inicial";

    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final long saldoInicial;

    public Ledger(CuentaRepository cuentas, MovimientoRepository movimientos,
                  @Value("${cafeorbe.wallet.saldo-inicial}") long saldoInicial) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.saldoInicial = saldoInicial;
    }

    /**
     * Asigna el saldo inicial configurado. Idempotente por usuario: si ya se cargó, no hace nada.
     *
     * @return {@code true} si se hizo la carga en esta llamada
     */
    @Transactional
    public boolean cargarSaldoInicial(UUID usuarioId) {
        if (movimientos.existsByUsuarioIdAndTipoAndReferencia(
                usuarioId, Movimiento.Tipo.CARGA_AUTOMATICA, REFERENCIA_CARGA_INICIAL)) {
            return false;
        }
        Cuenta cuenta = cuentas.findById(usuarioId).orElseGet(() -> cuentas.save(new Cuenta(usuarioId)));
        long saldo = cuenta.aplicar(saldoInicial);
        movimientos.save(new Movimiento(usuarioId, Movimiento.Tipo.CARGA_AUTOMATICA, saldoInicial, saldo,
                REFERENCIA_CARGA_INICIAL));
        return true;
    }

    /** Resultado de intentar un cobro y saldo del usuario después de procesarlo. */
    public record Cobro(Estado estado, long saldo) {
        public enum Estado {
            COBRADO,
            YA_COBRADO,
            SALDO_INSUFICIENTE
        }
    }

    /**
     * HU-20: descuenta al ganador el monto de la subasta que ganó. Un solo cobro por subasta: la referencia
     * es el id de la subasta, así que repetir el cobro no vuelve a descontar. El saldo nunca queda negativo:
     * si no alcanza, no se cobra nada.
     */
    @Transactional
    public Cobro cobrar(UUID usuarioId, long monto, String referencia) {
        if (movimientos.existsByUsuarioIdAndTipoAndReferencia(usuarioId, Movimiento.Tipo.COBRO, referencia)) {
            return new Cobro(Cobro.Estado.YA_COBRADO, saldoDe(usuarioId));
        }
        // Sin cuenta no hay de dónde cobrar, sea cual sea el monto: el ganador no es Comprador o su carga aún no llega.
        Cuenta cuenta = cuentas.findByIdForUpdate(usuarioId).orElse(null);
        if (cuenta == null) {
            return new Cobro(Cobro.Estado.SALDO_INSUFICIENTE, 0);
        }
        if (cuenta.getSaldo() < monto) {
            return new Cobro(Cobro.Estado.SALDO_INSUFICIENTE, cuenta.getSaldo());
        }
        long saldo = cuenta.aplicar(-monto);
        movimientos.save(new Movimiento(usuarioId, Movimiento.Tipo.COBRO, -monto, saldo, referencia));
        return new Cobro(Cobro.Estado.COBRADO, saldo);
    }

    /** Saldo actual; un usuario sin cuenta (no Comprador o carga aún en camino) tiene 0. */
    @Transactional(readOnly = true)
    public long saldoDe(UUID usuarioId) {
        return cuentas.findById(usuarioId).map(Cuenta::getSaldo).orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<Movimiento> ultimosMovimientos(UUID usuarioId, int limite) {
        return movimientos.findByUsuarioIdOrderByCreadoEnDesc(usuarioId, PageRequest.of(0, limite));
    }
}
