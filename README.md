# Prosess Task
Denne modulen implementerer rammeverk for å kjøre og fordele Prosess Tasks.

Dette er asynkrone bakgrunnsjobber som kjøres fordelt utover tilgjengelig maskiner.

Prosess Tasks kan fordeles i grupper der de kjøres sekvensielt og/eller i parallell.

Ytterligere dokumentasjon finnes her: [Automasjon](https://confluence.adeo.no/display/SVF/10.5+Tema+-+Automasjon).

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

## Set opp SubjectProvider (Optional)
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




### Licenses and attribution
*For updated information, always see LICENSE first!*

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen **#teamforeldrepenger**.

