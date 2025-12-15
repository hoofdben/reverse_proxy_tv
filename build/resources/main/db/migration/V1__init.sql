-- Initial baseline migration for Reverse Proxy TV
-- Create useful extensions and a simple table to verify connectivity

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS app_bootstrap (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
