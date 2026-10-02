package com.cafeorbe.wallet.repository;

import com.cafeorbe.wallet.model.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    /** Bloquea la cuenta mientras se le aplica un cobro, para que dos cobros simultáneos no se pisen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.usuarioId = :usuarioId")
    Optional<Cuenta> findByIdForUpdate(@Param("usuarioId") UUID usuarioId);
}
