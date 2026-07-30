-- Spring Modulith JPA event publication store
CREATE TABLE event_publication (
    id                      UUID                        NOT NULL PRIMARY KEY,
    completion_attempts     INTEGER,
    completion_date         TIMESTAMP WITH TIME ZONE,
    event_type              VARCHAR(512)                NOT NULL,
    last_resubmission_date  TIMESTAMP WITH TIME ZONE,
    listener_id             VARCHAR(512)                NOT NULL,
    publication_date        TIMESTAMP WITH TIME ZONE    NOT NULL,
    serialized_event        TEXT                        NOT NULL,
    status                  VARCHAR(50)                 NOT NULL
);

CREATE INDEX idx_event_publication_status ON event_publication (status);
CREATE INDEX idx_event_publication_listener_id ON event_publication (listener_id);
