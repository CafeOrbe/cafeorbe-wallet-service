package com.cafeorbe.wallet.repository;

import com.cafeorbe.wallet.model.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {
}
