package com.cafeorbe.wallet.controller;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.dto.SaldoDto;
import com.cafeorbe.wallet.service.Ledger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class OrbesController {

    public record MovimientoDto(UUID id, String tipo, long monto, long saldoResultante, String referencia, Instant fecha) {
    }

    private final Ledger ledger;

    public OrbesController(Ledger ledger) {
        this.ledger = ledger;
    }

    /** HU-06: saldo del usuario autenticado (a través del gateway). */
    @GetMapping("/api/orbes/saldo")
    public SaldoDto miSaldo(@RequestHeader(Cabeceras.USUARIO_ID) UUID usuarioId) {
        return new SaldoDto(usuarioId, ledger.saldoDe(usuarioId));
    }

    @GetMapping("/api/orbes/movimientos")
    public List<MovimientoDto> misMovimientos(@RequestHeader(Cabeceras.USUARIO_ID) UUID usuarioId) {
        return ledger.ultimosMovimientos(usuarioId, 50).stream()
                .map(m -> new MovimientoDto(m.getId(), m.getTipo().name(), m.getMonto(), m.getSaldoResultante(),
                        m.getReferencia(), m.getCreadoEn()))
                .toList();
    }

    /** Consulta síncrona que usa auction al validar una puja (HU-14). El gateway no la expone. */
    @GetMapping("/internal/orbes/{usuarioId}/saldo")
    public SaldoDto saldoDe(@PathVariable UUID usuarioId) {
        return new SaldoDto(usuarioId, ledger.saldoDe(usuarioId));
    }
}
