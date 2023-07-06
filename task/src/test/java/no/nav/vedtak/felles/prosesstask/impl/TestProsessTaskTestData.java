package no.nav.vedtak.felles.prosesstask.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;

public class TestProsessTaskTestData {

    private EntityManager entityManager;

    public TestProsessTaskTestData(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void slettAlleProssessTask() {
        entityManager.createQuery("delete from ProsessTaskEntitet ").executeUpdate();
    }

    public TestProsessTaskTestData opprettTask(ProsessTaskData prosessTask) {
        entityManager.persist(new ProsessTaskEntitet().kopierFraNy(prosessTask));
        return this;
    }
}
