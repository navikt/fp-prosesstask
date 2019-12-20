![](https://github.com/navikt/fp-prosesstask/workflows/Bygg%20og%20deploy/badge.svg) 
[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=alert_status)](https://sonarcloud.io/dashboard?id=navikt_fp-prosesstask) 
[![SonarCloud Coverage](https://sonarcloud.io/api/badges/measure?key=navikt_fp-prosesstask&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=navikt_fp-prosesstask)
[![SonarCloud Bugs](https://sonarcloud.io/api/badges/measure?key=navikt_fp-prosesstask&metric=bugs)](https://sonarcloud.io/component_measures/metric/reliability_rating/list?id=navikt_fp-prosesstask)
[![SonarCloud Vulnerabilities](https://sonarcloud.io/api/badges/measure?key=navikt_fp-prosesstask&metric=vulnerabilities)](https://sonarcloud.io/component_measures/metric/security_rating/list?id=navikt_fp-prosesstask)

# Prosess Task
Enkelt bibliotek for strukturerte tasks som kan kjøres på et cluster av maskiner, i definerte rekkefølger (sekvensielt, parallellt), med transaksjonstøtte og feilhåndtering.  

Denne modulen implementerer rammeverk for å kjøre og fordele Prosess Tasks.
Dette er asynkrone bakgrunnsjobber som kjøres fordelt utover tilgjengelig podder.  Muliggjør prosessering av transaksjoner og arbeidsflyt som paralle og sekvensielle oppgaver og kan spres utover 1 eller flere jvmer på ulike podder.

Ytterligere bakgrunnsinformasjon finnes her: [Automasjon](https://confluence.adeo.no/display/SVF/10.5+Tema+-+Automasjon).

ProsessTasks kan enten kjøres med en gang, i en definert rekkefølge, eller på angitt tid (eks. cron uttrykk).

# Bruk
En `ProsessTaskGruppe` definerer et sett med ProsessTask som skal kjøres sekvensielt/parallelt
Det er mulig å definere en digraph av prosesstasks i en jafs. 

Eksempel - task #1 kjøres først, deretter 4 ulike tasks  parallell(#2), så 2 tasks (#3) i parallell, til slutt kjøres #4.
```
      #2a
       +
       +
      #2b     #3a
       +       +
#1 +---------------+ #4
       +       +
      #2c     #3b
       +
       +
      #2d

```

Eksempel kode for over å sette opp over
```
	var pt1 = new ProsessTaskGruppe("oppgave 1");
	var pt2 = new ProsessTaskGruppe("oppgave 2a");
	...

	var gruppe = new ProsessTaskGruppe();
        gruppe
            .addNesteSekvensiell(pt1)
            .addNesteParallell(pt2a, pt2b, pt2c, pt2d);
			.addNesteParallell(pt3a, pt3b);
			.addNesteSekvensiell(pt4);
        ;

	ProsessTaskRepository repo = ...;

	repo.lagre(gruppe);

```

# Konfigurasjon og start

## Sett opp database tabeller.
Se `task/src/test/resources/db/migration/defaultDS` for eksempel tabell DDL (passer postgresql)

## Legg til i persistence.xml
Nåværende implementasjon forventer at `META-INF/pu-default.prosesstask.orm.xml`er definert i applikasjonens persistence.xml (eller tilsvarende) og dermed er en del av samme EntityManager som applikasjoen benytter. Det gir tilgang til å dele transaksjon for en kjøring (krever dermed ikke midlertidig bokføring for status på tasks eller egen opprydding når noe går galt utover å sette en task til KLAR igjen)

## Sett opp SubjectProvider (Optional)
Dette benyttes til sporing for endring av prosesstasks.
```
@ApplicationScoped
public class MinSubjectProvider implements SubjectProvider{

	public String getUserIdentity(){
	    // return user identity fra container el.
		return "navn.navnesen@nav.no";
	}
}
```

## Definer en ProsessTaskDispatcher
Dette er kobling mellom prosesstask data som er satt opp og implementasjon av en task.
```
@ApplicationScoped
public class MinTaskDispatcher implements ProsessTaskDispatcher {

	public void dispatch(ProsessTaskData task) throws Exception {
		
		String taskType = task.getTaskType();
		switch(taskType){
			case "innhent.inntektsopplysninger.task":
			    new InnhentInntektsOpplysningerTask().doRun(task.getAktørId());
				return;
		    default:
			    throw new UnsupportedOperationException("Ukjent task type " + taskType);
		}
	}
}
```


## Start TaskManager
TaskManager startes f.eks. fra en WebListener
```
@WebListener
	public class TaskManagerStarter implements ServletContextListener {
		
		@Override
		public void contextInitialized(ServletContextEvent sce) {
			// kan gjøre programmatisk lookup siden TaskManager er ApplicationScoped (en per applikasjoninstans)
			var taskManager = CDI.current().select(TaskManager.class).get();
			taskManager.start();
		}

		@Override
		public void contextDestroyed(ServletContextEvent sce) {
			var taskManager = CDI.current().select(TaskManager.class).get();
			taskManager.stop();
		}
	}
```

# Implementasjonsnotater

## Highlights ved implementasjon

1. Tasks polles fra database i rekkefølge de er definert (innenfor en gruppe)
2. Kjøring av tasks foregår i en transaksjon, denne deles med andre database operasjoner som utføres i tasken, avgrenset av savepoints for bokføring.
3. En task gruppe kan definere både sekvensielle og parallelle tasks (se Bruk).
4. Kun en maskin (jvm) vil kjøre en task til enhver tid. Dersom flere jvmer er satt opp fordeles tasks broderlig mellom dem.
5. Ved polling har jvmen 30sekunder til å påstarte arbeid på tasken, etter det står andre jvmer fritt til å stjele denne.

## Non-funtionals

1. Skalerbarhet:  avhenger av antall transaksjoner databasen kan kjøre samtidig og connections tilgjenglig på en pod. Hver JVM settes opp default med 3 worker threads for kjøring av tasks, og 1 poller thread.  Dvs. connection pool for database bør tillate minimum 3 connections for utelukkende kjøre tasks ved default oppsett (trenger normalt noen flere dersom andre ting kjøres i samme jvm).

## For utviklere
1. Se [TaskManager Polling SQL](https://github.com/navikt/fp-prosesstask/blob/master/task/src/main/resources/no/nav/vedtak/felles/prosesstask/impl/TaskManager_pollTask.sql) for algoritme som håndterer sekvensiell/parallell plukking av tasks, samt sørger for å fordele tasks utover ulike konsumenter. Dette gjøres vha. SQL WINDOW functions + SKIP LOCKED syntaks
2. Savepoints brukes til bokføring av kjørende tasks og feilhåndtering dersom noen tasks kaster exceptions.  Enkelte exceptions (SQLTransientException etc) oppfører seg transient og vil automatisk retryes, mens andre er avhengig av definert feilhåndteringalgoritme.



### Licenses and attribution
*For updated information, always see LICENSE first!*

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen **#teamforeldrepenger**.

