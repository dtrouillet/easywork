CREATE TABLE revinfo (
    rev     BIGSERIAL PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE document_aud (
    id          UUID NOT NULL,
    rev         BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    title       VARCHAR(500),
    status      VARCHAR(20),
    owner_id    VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE tag_aud (
    id      UUID NOT NULL,
    rev     BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    name    VARCHAR(100),
    PRIMARY KEY (id, rev)
);

CREATE TABLE correspondent_aud (
    id      UUID NOT NULL,
    rev     BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    name    VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE document_type_aud (
    id              UUID NOT NULL,
    rev             BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype         SMALLINT,
    name            VARCHAR(255),
    retention_days  INTEGER,
    PRIMARY KEY (id, rev)
);
