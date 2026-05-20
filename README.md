# Historical Reconstruction

## Introduction

Historical Reconstruction is a multiplayer web application where players compete by placing historical event cards in the correct chronological order. The backend provides a RESTful API that handles user authentication, game lobby management, real-time game logic, a friend system, a leaderboard, and event card data sourced from the Wikidata SPARQL API. It was developed as part of the Software Engineering Lab (SoPra) at the University of Zurich during the spring semester of 2026.
## Technologies

- [Spring Boot](https://spring.io/projects/spring-boot), Java backend framework
- [Java 17](https://www.oracle.com/java/), Programming language
- [Gradle](https://gradle.org/), Build tool
- [JPA / Hibernate](https://hibernate.org/), ORM for database access
- [H2](https://www.h2database.com/), In-memory database (local profile only)
- [PostgreSQL](https://www.postgresql.org/), Relational database (production, hosted on Google Cloud SQL)
- [Wikidata SPARQL API](https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service), External data source for historical event cards
- [Google App Engine](https://cloud.google.com/appengine), Cloud deployment platform
- [DiceBear](https://www.dicebear.com/), External source for Avatars

## High-Level Components

1. **[`GameController.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/GameController.java)** The primary REST controller for all game-related endpoints: creating/joining lobbies, submitting answers, and retrieving game state. Central entry point for the core game flow.
2. **[`TimelineGameService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/TimelineGameService.java)** The largest and most complex service class. Contains the core game logic: evaluating player card placements on the timeline, computing scores, and advancing rounds.
3. **[`GameLobbyService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameLobbyService.java)** Manages the lobby lifecycle: player joining, readiness, game configuration, and transitioning from lobby to active game. Works closely with [`GameStartService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/GameStartService.java).
4. **[`WikidataService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/WikidataService.java)** Fetches and parses historical event data from the external Wikidata API to populate [`EventCard`](src/main/java/ch/uzh/ifi/hase/soprafs26/entity/EventCard.java) objects used during gameplay. The primary external dependency.
5. **[`AuthService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/AuthService.java)** Handles user registration and login, token issuance, and authentication validation for protected endpoints. Works alongside [`UserService.java`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserService.java) for user profile management.
## Launch & Deployment

### Prerequisites

- Java 17 (`java-version`)
- Git

### IntelliJ

1. **File → Open** the project folder
2. Accept to import the project as a `gradle project`
3. To build right click the `build.gradle` file and choose `Run Build`

### VS Code

The following extensions can help you get started more easily:

-   `vmware.vscode-spring-boot`
-   `vscjava.vscode-spring-initializr`
-   `vscjava.vscode-spring-boot-dashboard`
-   `vscjava.vscode-java-pack`

>**Note:** You'll need to build the project first with Gradle, just click on the `build` command in the _Gradle Tasks_ extension. Then check the _Spring Boot Dashboard_ extension if it already shows `soprafs26` and hit the play button to start the server. If it doesn't show up, restart VS Code and check again.

### Building with Gradle

You can use the local Gradle Wrapper to build the application.

-   macOS: `./gradlew`
-   Linux: `./gradlew`
-   Windows: `./gradlew.bat`

More Information about [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) and [Gradle](https://gradle.org/docs/).

#### Build

```bash
./gradlew build
```

#### Run

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
You can verify that the server is running by visiting `localhost:8080` in your browser.

> **Note:** Always use the `local` Spring profile when running locally. Without it, the server
> will attempt to connect to the production PostgreSQL database on Google Cloud SQL, which
> requires credentials not available in a local environment.

#### Test

```bash
./gradlew test
```

Test coverage is tracked via [SonarQube](https://sonarcloud.io/). The project targets ≥75% coverage.
### Development Mode

You can start the backend in development mode, this will automatically trigger a new build and reload the application once the content of a file has been changed.

Start two separate terminal windows and run:

```bash
./gradlew build --continuous
```

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

If you don't want to run all tests with every change, use the following command instead:

```bash
./gradlew build --continuous -xtest
```
### Production Database

In production (Google App Engine), the server connects to a **PostgreSQL** database hosted on Google Cloud SQL (`sopra-fs26-group-31-server:europe-west6:sopra-fs26-group-31-db`). The database password is injected at deploy time via the `DB_PASSWORD` GitHub secret and the `app.yaml` environment variables. No manual database setup is needed for deployment.
### Deployment (Google App Engine)

All pushes to `main` automatically trigger the deployment workflow in

[`.github/workflows/main.yml`](.github/workflows/main.yml), which:

1. Runs tests and SonarQube analysis
2. Injects the `DB_PASSWORD` secret into `app.yaml`
3. Deploys to Google App Engine

The live server is available at:

**https://sopra-fs26-group-31-server.oa.r.appspot.com/**

To trigger a release manually, push a tagged commit to `main`:

```bash
git tag M#
git push origin M#
```

Ensure the following GitHub repository secrets are configured:
- `GCP_SERVICE_CREDENTIALS`
- `DB_PASSWORD`
- `SONAR_TOKEN`

## Roadmap

Top features that new contributors could add:


1. **WebSocket / SSE for real-time updates**: Replace the current polling-based approach with WebSockets or Server-Sent Events so game state pushes to all players instantly without repeated client requests.
2. **Expanded Wikidata categories**: Extend `WikidataService.java` to support additional historical domains (science, sports, geography) so players can choose a category before starting a game.
3. **Persistent leaderboard with seasons**: Upgrade the `LeaderboardService.java` to support seasonal resets and historical stats backed by the existing production PostgreSQL database.
## Authors and Acknowledgment

- Alex Wimmer ([AlexWimmer 1](https://github.com/AlexWimmer1))
- Arthur Maximilian Sandor Csaky-Pallavicini ([milchazor](https://github.com/milchazor))
- Colin Kreienbühl ([Fanelock](https://github.com/Fanelock))
- Marco Büchel ([marcokingo](https://github.com/marcokingo))
- Samuel Jonas Fessler ([sjfess](https://github.com/sjfess))

We thank our TA and the Software Engineering Lab teaching team for their guidance throughout the course.
## License

This project is licensed under the [Apache License 2.0](LICENSE).
