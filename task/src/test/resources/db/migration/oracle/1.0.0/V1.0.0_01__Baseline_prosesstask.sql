--------------------------------------------------------
-- DDL for Prosesstask spesifikt for VL-fordeling
-- Viktig å merke seg her at alt av DDL relatert til prosesstask-biten er ikke eid av dette prosjektet, DDL eies
-- av no.nav.vedtak.felles:felles-behandlingsprosess. Endringer i DDL skal gjøres i prosjektet som eier DDLen.
-- Kopiert fra: https://github.com/navikt/fp-abakus/blob/master/migreringer/src/main/resources/db/migration/defaultDS/1.0/V1.1_08__Baseline_prosesstask.sql
--------------------------------------------------------

CREATE TABLE PROSESS_TASK
(
    ID NUMBER(19) NOT NULL
        CONSTRAINT PK_PROSESS_TASK
            PRIMARY KEY,
    TASK_TYPE VARCHAR2(50 CHAR) NOT NULL,
    PRIORITET NUMBER(3) DEFAULT 0 NOT NULL,
    STATUS VARCHAR2(20 CHAR) DEFAULT 'KLAR' NOT NULL
        CONSTRAINT CHK_PROSESS_TASK_STATUS
            CHECK (STATUS IN ('KLAR', 'FEILET', 'VENTER_SVAR', 'SUSPENDERT', 'VETO', 'FERDIG', 'KJOERT')),
    TASK_PARAMETERE VARCHAR2(4000 CHAR),
    TASK_PAYLOAD CLOB,
    TASK_GRUPPE VARCHAR2(250 CHAR),
    TASK_SEKVENS VARCHAR2(100 CHAR) DEFAULT '1' NOT NULL,
    NESTE_KJOERING_ETTER TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    FEILEDE_FORSOEK NUMBER(5) DEFAULT 0,
    SISTE_KJOERING_TS TIMESTAMP(6),
    SISTE_KJOERING_FEIL_KODE VARCHAR2(50 CHAR),
    SISTE_KJOERING_FEIL_TEKST CLOB,
    SISTE_KJOERING_SERVER VARCHAR2(50 CHAR),
    VERSJON NUMBER(19) DEFAULT 0 NOT NULL,
    OPPRETTET_AV VARCHAR2(30 CHAR) DEFAULT 'VL' NOT NULL,
    OPPRETTET_TID TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    BLOKKERT_AV NUMBER(19),
    SISTE_KJOERING_PLUKK_TS TIMESTAMP(6),
    SISTE_KJOERING_SLUTT_TS TIMESTAMP(6)
);

COMMENT ON COLUMN PROSESS_TASK.ID IS 'Primary Key';
COMMENT ON COLUMN PROSESS_TASK.TASK_TYPE IS 'navn på task. Brukes til å matche riktig implementasjon';
COMMENT ON COLUMN PROSESS_TASK.PRIORITET IS 'prioritet på task.  Høyere tall har høyere prioritet';
COMMENT ON COLUMN PROSESS_TASK.STATUS IS 'status på task: KLAR, NYTT_FORSOEK, FEILET, VENTER_SVAR, FERDIG';
COMMENT ON COLUMN PROSESS_TASK.TASK_PARAMETERE IS 'parametere angitt for en task';
COMMENT ON COLUMN PROSESS_TASK.TASK_PAYLOAD IS 'inputdata for en task';
COMMENT ON COLUMN PROSESS_TASK.TASK_GRUPPE IS 'angir en unik id som grupperer flere ';
COMMENT ON COLUMN PROSESS_TASK.TASK_SEKVENS IS 'angir rekkefølge på task innenfor en gruppe ';
COMMENT ON COLUMN PROSESS_TASK.NESTE_KJOERING_ETTER IS 'tasken skal ikke kjøeres før tidspunkt er passert';
COMMENT ON COLUMN PROSESS_TASK.FEILEDE_FORSOEK IS 'antall feilede forsøk';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_TS IS 'siste gang tasken ble forsøkt kjørt';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_FEIL_KODE IS 'siste feilkode tasken fikk';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_FEIL_TEKST IS 'siste feil tasken fikk';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_SERVER IS 'navn på node som sist kjørte en task (server@pid)';
COMMENT ON COLUMN PROSESS_TASK.VERSJON IS 'angir versjon for optimistisk låsing';
COMMENT ON COLUMN PROSESS_TASK.BLOKKERT_AV IS 'Id til ProsessTask som blokkerer kjøring av denne (når status=VETO)';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_TS IS 'siste gang tasken ble forsøkt kjørt (før kjøring)';
COMMENT ON COLUMN PROSESS_TASK.SISTE_KJOERING_SLUTT_TS IS 'tidsstempel siste gang tasken ble kjørt (etter kjøring)';
COMMENT ON TABLE PROSESS_TASK IS 'Inneholder tasks som skal kjøres i bakgrunnen';


CREATE INDEX IDX_PROSESS_TASK_6
    ON PROSESS_TASK (BLOKKERT_AV);
CREATE INDEX IDX_PROSESS_TASK_2
    ON PROSESS_TASK (TASK_TYPE);
CREATE INDEX IDX_PROSESS_TASK_3
    ON PROSESS_TASK (NESTE_KJOERING_ETTER);
CREATE INDEX IDX_PROSESS_TASK_5
    ON PROSESS_TASK (TASK_GRUPPE);
CREATE INDEX IDX_PROSESS_TASK_1
    ON PROSESS_TASK (STATUS);

--------------------------------------------------------
--  Sequences
--------------------------------------------------------

CREATE SEQUENCE SEQ_PROSESS_TASK
    MINVALUE 1000000
    INCREMENT BY 50
    NOCACHE;

CREATE SEQUENCE SEQ_PROSESS_TASK_GRUPPE
    MINVALUE 10000000
    INCREMENT BY 1000000
    NOCACHE;
