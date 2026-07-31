package com.starsoft.voint.question;

/** Cavabsız sualın həyat dövrü. */
public enum QuestionStatus {
    /** Təhlil tapıb, hələ heç kim baxmayıb. */
    OPEN,
    /** Cavab bilik bazasına əlavə olunub (rag_document_id doludur). */
    ANSWERED,
    /** Operator "buna cavab lazım deyil" deyib bağlayıb. */
    DISMISSED
}
