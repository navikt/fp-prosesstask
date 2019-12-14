package no.nav.vedtak.felles.prosesstask;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

@ApplicationScoped
public class EntityManagerForTestProducer {

    @ApplicationScoped
    @Produces
    @Any
    public EntityManager createEntityManager() {
        return Persistence.createEntityManagerFactory("pu-default").createEntityManager();
    }
}
