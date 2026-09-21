-- Saldo materializado: se actualiza en la misma transacción que inserta el movimiento.
create table cuenta (
    usuario_id     uuid                     primary key,
    saldo          bigint                   not null,
    actualizado_en timestamp with time zone not null,
    constraint ck_cuenta_saldo_no_negativo check (saldo >= 0)
);

-- Libro de movimientos: filas inmutables. El saldo siempre es la suma de sus movimientos.
create table movimiento (
    id              uuid                     primary key,
    usuario_id      uuid                     not null,
    tipo            varchar(30)              not null,
    monto           bigint                   not null,
    saldo_resultante bigint                  not null,
    referencia      varchar(100)             not null,
    creado_en       timestamp with time zone not null,
    -- Un mismo hecho (carga inicial, cobro de una subasta) solo puede registrarse una vez por usuario.
    constraint uq_movimiento_hecho unique (usuario_id, tipo, referencia)
);

create index ix_movimiento_usuario on movimiento (usuario_id, creado_en);

-- Ids de eventos ya consumidos, para que un evento repetido no se procese dos veces.
create table evento_procesado (
    event_id      uuid                     primary key,
    procesado_en  timestamp with time zone not null
);
