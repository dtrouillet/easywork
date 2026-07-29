CREATE TABLE tag (
    id      UUID PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE,
    color   VARCHAR(7)
);

CREATE TABLE document_tag (
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);
