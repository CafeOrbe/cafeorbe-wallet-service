package com.cafeorbe.wallet.repository;

import com.cafeorbe.wallet.model.Movimiento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, UUID> {

    boolean existsByUsuarioIdAndTipoAndReferencia(UUID usuarioId, Movimiento.Tipo tipo, String referencia);

    List<Movimiento> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId, Pageable pagina);
}
