CREATE TABLE network_router_connection (
    id SMALLINT NOT NULL PRIMARY KEY,
    base_url VARCHAR(200) NOT NULL,
    username VARCHAR(80) NOT NULL,
    encrypted_password VARCHAR(2048) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_router_connection_singleton CHECK (id = 1)
);
