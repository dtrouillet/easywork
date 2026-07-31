-- revinfo_seq: used by Hibernate Envers for revision IDs (allocationSize=50 by default)
CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev     BIGINT NOT NULL DEFAULT nextval('revinfo_seq') PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE document_aud (
    id              UUID NOT NULL,
    rev             BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype         SMALLINT,
    status          VARCHAR(20),
    ocr_applied     BOOLEAN,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    trashed_at      TIMESTAMP WITH TIME ZONE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, rev)
);

CREATE TABLE tag_aud (
    id      UUID NOT NULL,
    rev     BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    name    VARCHAR(100),
    color   VARCHAR(20),
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
