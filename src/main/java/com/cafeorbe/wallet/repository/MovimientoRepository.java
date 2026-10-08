package com.cafeorbe.wallet.repository;

import com.cafeorbe.wallet.model.Movimiento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, UUID> {

    boolean existsByUsuarioIdAndTipoAndReferencia(UUID usuarioId, Movimiento.Tipo tipo, String referencia);

    List<Movimiento> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId, Pageable pagina);

    List<Movimiento> findByUsuarioIdAndTipoOrderByCreadoEnDesc(UUID usuarioId, Movimiento.Tipo tipo, Pageable pagina);

    /** Suma de los movimientos de un tipo; 0 si no hay ninguno. */
    @Query("select coalesce(sum(m.monto), 0) from Movimiento m where m.usuarioId = :usuarioId and m.tipo = :tipo")
    long sumarPorTipo(@Param("usuarioId") UUID usuarioId, @Param("tipo") Movimiento.Tipo tipo);
}
