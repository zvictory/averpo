--liquibase formatted sql

--changeset averpo:042-01-attachment
-- Attachment - транзакция ҳужжатига файл бириктириш (docs/modules/
-- attachments.md, QBO Attachments паритети). GL'га мутлақо тегмайди -
-- соф ҳужжат иловаси. Полиморф боғланиш: document_type (DocumentType
-- enum номи) + document_id; FK ЙЎҚ (ҳар модул жадвалига боғланмайди -
-- сервис target мавжудлигини JdbcClient EXISTS билан ўзи текширади,
-- BR-ATT-003). Файлнинг ўзи локал дискда (app.attachments.dir), базада
-- фақат метамаълумот - backup/қувват оғирлашмайди. stored_path диск
-- номи ФАҚАТ сервер UUID (йил/ой/UUID.ext) - фойдаланувчи киритган ном
-- диск йўлига кирмайди (path traversal ҳимояси).
CREATE TABLE attachment (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    document_type VARCHAR(30) NOT NULL,
    document_id   UUID NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes >= 0)
);
CREATE INDEX idx_attachment_document ON attachment(document_type, document_id);
--rollback DROP TABLE attachment;
