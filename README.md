[![Bygg og deploy](https://github.com/navikt/fp-prosesstask/actions/workflows/build.yml/badge.svg)](https://github.com/navikt/fp-prosesstask/actions/workflows/build.yml)

[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=alert_status)](https://sonarcloud.io/dashboard?id=navikt_fp-prosesstask)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=navikt_fp-prosesstask)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=navikt_fp-prosesstask)
[![SonarCloud Bugs](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=bugs)](https://sonarcloud.io/component_measures/metric/reliability_rating/list?id=navikt_fp-prosesstask)
[![SonarCloud Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=vulnerabilities)](https://sonarcloud.io/component_measures/metric/security_rating/list?id=navikt_fp-prosesstask)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=navikt_fp-prosesstask&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=navikt_fp-prosesstask)

![GitHub release (latest by date)](https://img.shields.io/github/v/release/navikt/fp-prosesstask)
![GitHub](https://img.shields.io/github/license/navikt/fp-prosesstask)

# Prosess Task
Enkelt bibliotek for strukturerte tasks som kan kjøres på et cluster av maskiner, i definerte rekkefølger (sekvensielt, parallellt), med transaksjonstøtte og feilhåndtering.  

Denne modulen implementerer rammeverk for å kjøre og fordele Prosess Tasks.  En prosess task kan anses som en jobb som kjører asynkront, transaksjonelt, og kan kjøre avhengig av andre jobber. Ved feil under kjøring kan applikasjonen beskrive en feilhåndteringsalgoritme som skal benyttes før rekjøring eller varsling.

Prosesstasks er asynkrone bakgrunnsjobber som kjøres fordelt utover tilgjengelig podder.  Muliggjør prosessering av transaksjoner og arbeidsflyt som paralle og sekvensielle oppgaver og kan spres utover 1 eller flere jvmer på ulike podder.

ProsessTasks kan enten kjøres med en gang, i en definert rekkefølge, eller på angitt tid (eks. cron uttrykk).

## Use Cases
De benyttes som en grunnleggende byggekloss for å implementere følgende, med transaksjonssikkerhet, og skalering over et cluster av maskiner og tråder:

* [Outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html). Ved kall til REST/Kafka/MQ/etc tjenester som ikke inngår i samme transaksjon som oppdatering av egen database. Tjenesten som kalles på bør være idempotent / ha støtte for at-least-once semantikk.
* [Inbox pattern] Fanger innkommende meldinger lokalt før videre prosessering internt (dvs. overtar ansvar for videre håndtering fra tjeneste som avleverer melding.
* [Saga pattern](https://microservices.io/patterns/data/saga.html) Orkestrert Saga pattern i samspill med Outbox pattern.
* Scheduled jobs (cron).  Kan angis som tasks som starter regelmessig


## Ta i bruk

### Definer i Maven DependencyManagement
```
        -- legg til i root pom
	<dependency>
		<groupId>no.nav.vedtak.prosesstask</groupId>
		<artifactId>prosesstask-root</artifactId>
		<version>3.0.1</version>
		<scope>import</scope>
		<type>pom</type>
	</dependency>
```

### Bruk 
Vanlig bruk. Krever at applikasjonen har en egen implementasjon av ProsessTaskDispatcher (se Konfigurasjon under)
```
	<dependency>
	    <groupId>no.nav.vedtak.prosesstask</groupId>
	    <artifactId>prosesstask</artifactId>
	</dependency>
```

### Legacy bruk
Denne kommer med avhengighet til felles-kontekst, og setter kontekst  ved hver kjøring av task (i `KontekstCdiProsessTaskDispatcher`).


```
	<dependency>
	    <groupId>no.nav.vedtak.prosesstask</groupId>
	    <artifactId>prosesstask-kontekst</artifactId>
	</dependency>
```



# Anvendelse

## Definere en Prosesstask

Eksempelkode for å definere en prosesstask som kan opprettes og så kjøres av rammeverket

Prosesstasks som defineres må oppfylle følgende krav:
* implementere ProsessTaskHandler og metoden doTask + eventuel custom retrylogikk
* annoteres med @ProsessTask der value er påkrevd og må være samme tekst som brukes når det opprettes/lagres en task.
* annoteres så den kan oppdages av CDI (Normalt ApplicationScoped eller Dependent)

Man kan gjerne legge på en annoterting @ProsessTaskDocumentation med beskrivelse av funksjon
Dessuten kan @ProsessTask også inneholde  en cronExpression som angir et cron-uttrykk for kjeding og kjøring av neste instans og konfigurasjon av feilhåndteringen. 
Default feilhåndtering er retry et antall ganger med økende pause - standard er å gi opp etter maxRuns = 3 med pauser firstDelay = 30 (sekunder) og thenDelay = 60

```
@ProsessTask("hello.world") 
@ProsessTaskDocumentation("Eksempeltasktype for illustrasjon")
@ApplicationScoped
public class HelloWorldTask implements ProsessTaskHandler {

    @Override
    public void doTask(ProsessTaskData taskData) {
        System.out.println("Hello world fra task " + taskData.taskType().value());
    }

} 

public class BrukProsessTask {

   public void opprettHelloWorld() {
       ProsessTaskData task = ProsessTaskData
          .forProsessTaskHandler(HelloWorldTask.class);
       // Lagrer task i eksisterende Tx. Etter commit blir task utført i egen tråd/tx.
       prosessTaskTjeneste.lagre(task); 
   }
}
```

## ProsesstaskGruppe - for Sekvensielle og Parallelle Prosesstasks

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

## Sett opp SubjectProvider 
*(NB: brukes ikke dersom prosesstask-kontekst brukes)*
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
*(NB: brukes ikke dersom prosesstask-kontekst brukes)*
Dette er kobling mellom prosesstask data som er satt opp og implementasjon av en task.
```
@ApplicationScoped
public class MinTaskDispatcher implements ProsessTaskDispatcher {

	public void dispatch(ProsessTaskData task) throws Exception {
		
		TaskType taskType = task.getTaskType();
		switch(taskType){
			case "innhent.inntektsopplysninger.task":
			    new InnhentInntektsOpplysningerTask().doRun(task.getAktørId());
				return;
		    default:
			    throw new UnsupportedOperationException("Ukjent task type " + taskType);
		}
	}
	
	public boolean feilhåndterException(Throwable e) {
	    return <OurRetryableException>.isAssignableFrom(e.getClass())
	}

    public ProsessTaskHandlerRef taskHandler(TaskType taskType) {
        return new ProsessTaskHandlerRef(<bean lookup taskType>);
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
2. Kjøring av tasks foregår i en transaksjon, denne deles med andre database operasjoner som utføres i tasken, avgrenset av savepoints for bokføring (for feilhåndtering etc).
3. En task gruppe kan definere både sekvensielle og parallelle tasks (se Bruk).
4. Kun en maskin (jvm) vil kjøre en task til enhver tid. Dersom flere jvmer er satt opp fordeles tasks broderlig mellom dem.
5. Ved polling har jvmen 30sekunder til å påstarte arbeid på tasken, etter det står andre jvmer fritt til å stjele denne.

## Non-funtionals

1. Skalerbarhet:  avhenger av antall transaksjoner databasen kan kjøre samtidig og connections tilgjenglig på en pod. Hver JVM settes opp default med 3 worker threads for kjøring av tasks, og 1 poller thread.  Dvs. connection pool for database bør tillate minimum 3 connections for utelukkende kjøre tasks ved default oppsett (trenger normalt noen flere dersom andre ting kjøres i samme jvm).

## For utviklere
1. Se [TaskManager Polling SQL](https://github.com/navikt/fp-prosesstask/blob/master/task/src/main/resources/no/nav/vedtak/felles/prosesstask/impl/TaskManager_pollTask.sql) for algoritme som håndterer sekvensiell/parallell plukking av tasks, samt sørger for å fordele tasks utover ulike konsumenter. Dette gjøres vha. SQL WINDOW functions + SKIP LOCKED syntaks. Dette er hjertet av hele dette bibliotektet. Alt annet er støtte feilhåndtering, konfigurasjon, dispatch og API.
2. Savepoints brukes til bokføring av kjørende tasks og feilhåndtering dersom noen tasks kaster exceptions.  Enkelte exceptions (SQLTransientException etc) oppfører seg transient og vil automatisk retryes, mens andre er avhengig av definert feilhåndteringalgoritme. Hvilke defineres av angitt implementasjon av `ProsessTaskDispatcher`

## Statuser
Tasks har en livssyklus av statuser
```
		       +-------->VENTER_SVAR
		       |                 +
		       |                 |
		       +                 v
		    KLAR+------------>KJOERT+-------->FERDIG
		     + ^
		     | |
		     v +
		SUSPENDERT/
		VETO

```
* KLAR - Alle som har denne vil bli kjørt såfremt deres tid, sekvens, gruppe og prioritet matcher
* KJOERT - Task er ferdig. Er en pseudo-status som markerer ferdig, men i samme partisjon
* FERDIG - Task er ferdig og arkivert til partisjon
* SUSPENDERT - vil stoppe kjøring av alle tasks i samme gruppe. Kan kun settes utenfra
* VETO - en task kan legge ned veto mot andre i samme gruppe, inntil denne er kjørt. Kan kun settes ved kjøring
* VENTER_SVAR - en task kan settes på pause inntil den reaktiveres av en hendelse. Kan kun settes ved kjøring

Det er ikke noe eget status for når tasken kjører, i dette øyeblikket vil det holdes en lås på raden til den er ferdig kjørt. Dette gjøres, som alternativ til egne statuser for kjøring, da det minimerer antall transaksjoner nødvendig, og unngår administrasjon og opprydding av tasks hvis de krasjer.

## Work Stealing
Når en tasks polles (gjøres klart til å kjøres) vil det flagges et vindu på 30s til en node kan ha påbegynt en task.  Dersom noden ikke er ferdig (har KJOERT) tasken innen dette vinduet, eller påbegynt den (tatt rad lås) så står andre noder fritt til å prøve å ta tak i den. Når andre noder tar tak i en task har de også et vindu før andre noder kan stjele den.  Første som faktisk påbegynner en task etter polling vinner, mens de andre vil ignorere kjøring.  Dette unngår også administrasjon og opprydding av tasks som krasjer, samtidig som det fordeler lasten bedre over nodene.

### Licenses and attribution
*For updated information, always see LICENSE first!*

### For Nav-ansatte
Interne henvendelser kan sendes via Slack i kanalen **#teamforeldrepenger**.

