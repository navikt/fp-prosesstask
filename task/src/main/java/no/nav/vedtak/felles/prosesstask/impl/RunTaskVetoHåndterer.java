package no.nav.vedtak.felles.prosesstask.impl;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Optional;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.feil.Feil;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskFeil;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskLifecycleObserver;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskVeto;

public class RunTaskVetoHåndterer {
    private static final Logger log = LoggerFactory.getLogger(RunTaskVetoHåndterer.class);

    private ProsessTaskEventPubliserer eventPubliserer;
    private EntityManager em;

    private TaskManagerRepositoryImpl taskManagerRepo;

    public RunTaskVetoHåndterer(ProsessTaskEventPubliserer eventPubliserer, TaskManagerRepositoryImpl taskManagerRepo, EntityManager entityManager) {
        this.eventPubliserer = eventPubliserer;
        this.taskManagerRepo = taskManagerRepo;
        this.em = entityManager;
    }

    /**
     * @param blokkerendeTask
     * @return true dersom har frigitt noen veto, false hvis det ikke var noen.
     */
    boolean frigiVeto(ProsessTaskEntitet blokkerendeTask) {

        String updateSql = "update PROSESS_TASK SET "
            + " status='KLAR'"
            + ", blokkert_av=NULL"
            + ", siste_kjoering_feil_kode=NULL"
            + ", siste_kjoering_feil_tekst=NULL"
            + ", neste_kjoering_etter=NULL"
            + ", versjon = versjon +1"
            + " WHERE blokkert_av=:id";

        int frigitt = em.createNativeQuery(updateSql)
            .setParameter("id", blokkerendeTask.getId())
            .executeUpdate();

        if (frigitt > 0) {
            log.info("ProsessTask [id={}, taskType={}] FERDIG. Frigitt {} tidligere blokkerte tasks", blokkerendeTask.getId(), blokkerendeTask.getTaskName(),
                frigitt);
            return true;
        }
        return false; // Har ikke hatt noe veto å frigi
    }

    boolean vetoRunTask(ProsessTaskEntitet pte) throws IOException {
        if (eventPubliserer == null) {
            return false;
        }

        HandleProsessTaskLifecycleObserver lifecycleObserver = eventPubliserer.getHandleProsessTaskLifecycleObserver();
        ProsessTaskData prosessTaskData = pte.tilProsessTask();

        boolean vetoed = false;

        Optional<SimpleEntry<ProsessTaskLifecycleObserver, ProsessTaskVeto>> vetoRunTask = lifecycleObserver.vetoRunTask(prosessTaskData);
        if (vetoRunTask.isPresent()) {
            SimpleEntry<ProsessTaskLifecycleObserver, ProsessTaskVeto> entry = vetoRunTask.get();
            ProsessTaskVeto veto = entry.getValue();
            if (veto.isVeto()) {
                vetoed = true;
                Long blokkerId = veto.getBlokkertAvProsessTaskId();

                sjekkBlokker(blokkerId);

                Feil feil = TaskManagerFeil.FACTORY.kanIkkeKjøreFikkVeto(pte.getId(), pte.getTaskName(), blokkerId, veto.getBegrunnelse());
                ProsessTaskFeil taskFeil = new ProsessTaskFeil(pte.tilProsessTask(), feil);
                taskFeil.setBlokkerendeProsessTaskId(blokkerId);
                pte.setSisteFeil(taskFeil.getFeilkode(), taskFeil.writeValueAsString());

                pte.setBlokkertAvProsessTaskId(blokkerId);
                pte.setStatus(ProsessTaskStatus.VETO); // setter også status slik at den ikke forsøker på nytt. Blokkerende task må resette denne.
                pte.setNesteKjøringEtter(null); // kjør umiddelbart når veto opphører

                em.persist(pte);
                em.flush();
            }
        }

        return vetoed;
    }

    private void sjekkBlokker(Long blokkerId) {
        
    }
}
