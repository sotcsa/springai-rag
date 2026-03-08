-- =============================================
-- V1: Create document tracking table
-- =============================================

CREATE TABLE DOCUMENTS (
    ID             VARCHAR2(36)   PRIMARY KEY,
    FILE_NAME      VARCHAR2(500)  NOT NULL,
    CONTENT_TYPE   VARCHAR2(100),
    FILE_SIZE      NUMBER(19),
    CHUNK_COUNT    NUMBER(10),
    STATUS         VARCHAR2(20)   NOT NULL,
    ERROR_MESSAGE  VARCHAR2(2000),
    CREATED_AT     TIMESTAMP      NOT NULL,
    UPDATED_AT     TIMESTAMP
);

CREATE INDEX IDX_DOCUMENTS_STATUS ON DOCUMENTS(STATUS);
CREATE INDEX IDX_DOCUMENTS_CREATED ON DOCUMENTS(CREATED_AT DESC);

-- Note: The VECTOR_STORE table is created automatically by
-- Spring AI OracleVectorStore with initializeSchema=true.
-- It creates:
--   VECTOR_STORE (
--     ID        VARCHAR2(36) PRIMARY KEY,
--     CONTENT   CLOB,
--     METADATA  JSON,
--     EMBEDDING VECTOR(768, FLOAT32)
--   )
-- with an IVF vector index for cosine similarity.
