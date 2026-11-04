--liquibase formatted sql

--changeset app:create-orders
CREATE TABLE orders (
    id     VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);
