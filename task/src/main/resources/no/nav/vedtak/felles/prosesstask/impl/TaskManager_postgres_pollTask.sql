/**
 * Her ligger all logikk for å fordele tasker på tvers av flere pollere,
 * plukke tasker avhengig av om der klare for å kjøres (status + neste_kjoering_etter tid er passert), og
 * besørge at tasker kjøres i rekkefølge de var ment (sekvensielt eller parallellt).
 * <p>
 * SELECT'en er basert på to ting som er verdt å forstå når man leser denne.
 * <ul>
 * <li>SELECT FOR UPDATE SKIP LOCKED: Benyttes til å spre tasker på tvers av flere pollere slik at de ikke går i
 * beina på hverandre. Gir dermed mulighet for økt skalerbarhet og fleksible kjøring</li>
 * <li>Non-ANSI optimalisering for Postgres: Bruker DISTINCT ON for å finne første sekvensnummer i en gruppe, i common table expression (with)
 * Når det er skrevet slik, velger postgresql å kjøre det aggregeringen i CTE først, som gjør at spørringen er raskere
 *  (spesielt når grupper har mange sekvensnummer). </li>
 * </ul>
 */
WITH foerste_prosesstasker AS (
    SELECT DISTINCT ON (task_gruppe) task_gruppe, task_sekvens
    FROM prosess_task
    -- bruker dette istdf. (status NOT IN('FERDIG', 'KJOERT')). Innført for å bruke partisjoner med minst data, unngår skanning av alle partisjoner
    WHERE status IN ('FEILET', 'KLAR', 'VENTER_SVAR', 'SUSPENDERT', 'VETO')
    ORDER BY task_gruppe, length(task_sekvens), task_sekvens)
SELECT pt.*
FROM prosess_task pt
         join foerste_prosesstasker fp on fp.task_gruppe = pt.task_gruppe and fp.task_sekvens = pt.task_sekvens
WHERE pt.status = 'KLAR'
  -- fjerner de som har mindre enn maks antall feilede forsøk
  -- håndterer at kjøring ikke skjer før angitt tidstempel
  AND (pt.neste_kjoering_etter IS NULL OR pt.neste_kjoering_etter < :neste_kjoering)
  AND pt.id NOT IN (:skip_ids) -- sjekk mot skip ids i ytre loop ellers paavirkes rekkefølge
  -- sorter etter prioritet og når de sist ble kjørt
ORDER BY prioritet DESC, siste_kjoering_ts ASC NULLS FIRST, ID ASC
         FOR UPDATE SKIP LOCKED
