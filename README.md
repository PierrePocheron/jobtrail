# JobTrail — Apprendre le fullstack Quarkus + Angular en construisant une vraie app

> **Cours pratique et progressif.** On construit **JobTrail**, une application de suivi de candidatures, en partant de zéro :
> backend **Quarkus** (Java), base **PostgreSQL**, frontend **Angular**, **sécurité**, et déploiement **cloud-native** (Docker, CI/CD).
>
> Ce dépôt est **public** et pensé comme un **support de cours** : chaque partie explique le *pourquoi*, le vocabulaire, puis le code pas à pas. Pour chaque concept Quarkus, l'équivalent **Spring** est indiqué.
>
> *Document vivant — complété au fil des parties.*

---

## Sommaire

- [Pile technique](#pile-technique)
- [Organisation du projet](#organisation-du-projet)
- [P0 — Mise en place de l'environnement](#p0--mise-en-place-de-lenvironnement)
- [P1 — Premier endpoint REST avec Quarkus](#p1--premier-endpoint-rest-avec-quarkus)
- [P2 — PostgreSQL et Hibernate Panache](#p2--postgresql-et-hibernate-panache)
- [Annexe — Stratégie Docker](#annexe--stratégie-docker)
- [Feuille de route](#feuille-de-route)

---

## Pile technique

| Couche | Choix | Rôle |
|---|---|---|
| Langage backend | **Java 21+** (LTS) | Langage de l'API |
| Framework backend | **Quarkus 3** | Framework cloud-native (REST, injection, ORM, sécurité) |
| Persistance | **Hibernate ORM + Panache** | Mapping objets ↔ tables SQL, sans boilerplate |
| Base de données | **PostgreSQL** (conteneur Docker) | Stockage des données |
| Frontend | **Angular 19/20** | Interface (standalone components, signals) |
| Sécurité | **JWT / rôles, OWASP** | Authentification & autorisation |
| Build backend | **Maven** | Dépendances, compilation, tests |
| DevOps | **Docker, GitHub Actions** | Conteneurisation, CI/CD |

---

## Organisation du projet

### Arborescence du monorepo

```
jobtrail/
├── README.md          ← ce cours
├── compose.yaml       ← services Docker (PostgreSQL, Adminer)
├── backend/           ← API Quarkus
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/jobtrail/   ← code Java
│       │   └── resources/           ← application.properties, import.sql
│       └── test/java/com/jobtrail/  ← tests
└── frontend/          ← application Angular (P4)
```

### Règle de base : un dossier = un package

En Java, l'arborescence des dossiers **doit refléter** la déclaration `package`. Un fichier rangé dans `.../com/jobtrail/candidature/` commence obligatoirement par `package com.jobtrail.candidature;`. De plus, **le nom du fichier doit être identique au nom de la classe publique** (`Candidature.java` ⇄ `class Candidature`).

### Deux façons d'organiser : par couche ou par feature

| Approche | Structure | Quand l'utiliser |
|---|---|---|
| **Par couche** (package-by-layer) | `controller/`, `service/`, `repository/`, `entity/` | tutoriels, petits projets ; courant dans le monde Spring historique |
| **Par feature** (package-by-feature) ✅ | `candidature/`, `entreprise/`, `user/` (chaque dossier contient sa Resource, son Service, son entité…) | projets qui grandissent : meilleure cohésion, navigation plus simple, évolue vers le modulaire/microservices |

**Choix retenu : par feature.** Tout ce qui concerne « candidature » est regroupé. En entretien, l'important n'est pas de désigner « la meilleure » mais de **savoir justifier son choix** — les deux sont valables, la clé est la cohérence.

### Les couches (responsabilités), quel que soit le découpage

| Couche | Classe | Rôle | Équivalent Spring |
|---|---|---|---|
| Présentation (API) | `*Resource` | Reçoit les requêtes HTTP, renvoie le JSON | `@RestController` |
| Métier | `*Service` | Logique applicative, règles métier | `@Service` |
| Persistance | `*Repository` ou Panache | Accès aux données | `@Repository` |
| Domaine | `@Entity`, DTO, enum | Les données | `@Entity` |

> Pour l'instant, **Panache (active record)** nous évite d'écrire un Service et un Repository. On ajoutera un `CandidatureService` en **P3**, quand viendront la logique métier et la règle de sécurité « chaque utilisateur ne voit que ses propres candidatures ».

### Où va chaque fichier ?

| Fichier | Emplacement |
|---|---|
| Entités, Resources, Services (`.java`) | `backend/src/main/java/com/jobtrail/<feature>/` |
| `application.properties`, `import.sql` | `backend/src/main/resources/` |
| Tests | `backend/src/test/java/com/jobtrail/<feature>/` |
| `compose.yaml` | racine `jobtrail/` |

---

## P0 — Mise en place de l'environnement

### Outils nécessaires

| Outil | Vérifier avec | Rôle |
|---|---|---|
| **JDK 21+** | `java -version` | Machine virtuelle Java (compile et exécute) |
| **Maven** | `mvn -v` | Outil de build (≈ `npm` côté Java) |
| **Quarkus CLI** | `quarkus --version` | Créer/lancer/étendre un projet Quarkus |
| **Node LTS** (≥ 18.19) | `node -v` | Nécessaire pour Angular |
| **Docker** | `docker --version` | Conteneurs (base de données, déploiement) |
| **Git** | `git --version` | Versionnement |

### Installation (macOS, via Homebrew)

```bash
# Quarkus CLI (attention à "quarkusio", pas "quarkus")
brew install quarkusio/tap/quarkus

# Node LTS (si besoin) — ou via nvm
brew install node
```

> **Vocabulaire — JVM / JDK / LTS** : la **JVM** exécute le bytecode Java. Le **JDK** = la JVM + les outils pour compiler. **LTS** (Long Term Support) = version maintenue plusieurs années, celle qu'on utilise en entreprise (Java 21, 25…).

---

## P1 — Premier endpoint REST avec Quarkus

### Générer le projet

```bash
mkdir -p jobtrail && cd jobtrail
quarkus create app com.jobtrail:backend --extension=rest-jackson --java=21
```

- **`com.jobtrail:backend`** = `groupId:artifactId`.
  - `groupId` (`com.jobtrail`) : identifiant de l'organisation en domaine inversé = **package Java de base**.
  - `artifactId` (`backend`) : nom de l'artefact = **nom du dossier** créé.
- **`--extension=rest-jackson`** : une **extension** Quarkus = une librairie intégrée au framework. Ici : endpoints REST + sérialisation **JSON** (Jackson).
- **`--java=21`** : on cible Java 21.

### Structure générée

| Élément | Rôle | Analogie front |
|---|---|---|
| `pom.xml` | Config Maven (dépendances = extensions, build) | `package.json` |
| `mvnw` | Maven wrapper (build sans installer Maven) | `npx` |
| `src/main/java/…` | Code Java | `src/` |
| `src/main/resources/application.properties` | Configuration centrale typée | `.env` |
| `src/test/java/…` | Tests (JUnit + RestAssured) | `__tests__/` |

### Le mode dev

```bash
cd backend
quarkus dev
```

Le **mode dev** fait du **live reload pour Java** : tu modifies un fichier, tu rafraîchis le navigateur, Quarkus recompile à la volée. C'est l'équivalent du HMR du front, mais pour du Java — inhabituel et puissant.

- Endpoint d'exemple : http://localhost:8080/hello
- **Dev UI** (tableau de bord) : http://localhost:8080/q/dev-ui/

### Anatomie d'un endpoint (Jakarta REST / ex-JAX-RS)

```java
@Path("/hello")                       // URL de base de la ressource
public class GreetingResource {
    @GET                              // répond aux requêtes HTTP GET
    @Produces(MediaType.TEXT_PLAIN)   // type de contenu renvoyé
    public String hello() {           // handler : ce qu'on return = corps de la réponse
        return "Hello from Quarkus REST";
    }
}
```

Une classe annotée `@Path` est une **« ressource »** = un contrôleur. Les annotations *sont* le routing.

| Concept | Quarkus (Jakarta REST) | Spring | Express/Nest |
|---|---|---|---|
| Contrôleur | classe `@Path` | `@RestController` | router/controller |
| Route | `@Path("/x")` | `@RequestMapping("/x")` | `app.use("/x")` |
| Verbe HTTP | `@GET`, `@POST`… | `@GetMapping`… | `.get()`, `.post()` |
| Réponse JSON | `@Produces(APPLICATION_JSON)` | (défaut) | `res.json(obj)` |

### Premier endpoint JSON

Un **`record`** Java (porteur de données immuable, ≈ `type` TypeScript mais c'est une vraie classe) :

> 📄 `backend/src/main/java/com/jobtrail/candidature/Candidature.java` *(version P1, sera remplacée par une entité en P2)*

```java
package com.jobtrail.candidature;

public record Candidature(Long id, String entreprise, String poste, String statut) {}
```

Une ressource qui renvoie une liste en JSON (Jackson sérialise automatiquement) :

> 📄 `backend/src/main/java/com/jobtrail/candidature/CandidatureResource.java`

```java
package com.jobtrail.candidature;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/candidatures")
@Produces(MediaType.APPLICATION_JSON)
public class CandidatureResource {
    @GET
    public List<Candidature> list() {
        return List.of(
            new Candidature(1L, "Google", "Backend Java", "ENTRETIEN"),
            new Candidature(2L, "Doctolib", "Fullstack", "CANDIDATE")
        );
    }
}
```

→ http://localhost:8080/api/candidatures renvoie un tableau JSON. **Aucune** ligne de sérialisation à écrire.

---

## P2 — PostgreSQL et Hibernate Panache

### Vocabulaire

- **ORM** (Object-Relational Mapping) : couche qui fait le pont entre **objets Java** et **tables SQL**. Tu manipules des objets, l'ORM génère le SQL.
- **JPA** (Jakarta Persistence API) : la **norme** Java pour l'ORM (`@Entity`, `@Id`…).
- **Hibernate** : l'**implémentation** la plus utilisée de cette norme.
- **Panache** : la surcouche Quarkus qui **supprime le boilerplate** d'Hibernate.

### Étape 1 — PostgreSQL dans Docker

> 📄 `compose.yaml` (à la racine `jobtrail/`)

```yaml
services:
  postgres:
    image: postgres:16
    container_name: jobtrail-db
    environment:
      POSTGRES_DB: jobtrail
      POSTGRES_USER: jobtrail
      POSTGRES_PASSWORD: jobtrail
    ports:
      - "5432:5432"
    volumes:
      - jobtrail-pgdata:/var/lib/postgresql/data

  adminer:                 # interface web pour visualiser la base (optionnel)
    image: adminer:latest
    ports:
      - "8081:8080"

volumes:
  jobtrail-pgdata:         # volume nommé = les données survivent aux redémarrages
```

```bash
docker compose up -d       # démarre Postgres (+ Adminer) en arrière-plan
docker compose ps          # vérifier que ça tourne
docker compose down        # arrêter (les données restent dans le volume)
```

- **`image: postgres:16`** : version **figée** (bonne pratique : reproductibilité).
- **`environment`** : crée la base `jobtrail` avec l'utilisateur/mot de passe.
- **`ports: "5432:5432"`** : `PORT_HÔTE:PORT_CONTENEUR` — expose Postgres vers ta machine (`localhost:5432`).
- **`volumes`** : un **volume nommé** persiste les données hors du conteneur → pas de perte au redémarrage.
- **Adminer** : http://localhost:8081 — login `Système = PostgreSQL`, `Serveur = postgres`, `Utilisateur/Mot de passe/Base = jobtrail`.

> **Réseau Docker** : depuis ta machine (où tourne `quarkus dev`), la base est sur **`localhost:5432`**. Depuis Adminer (qui est *dans* le réseau Docker), l'hôte est le **nom du service `postgres`**. Chaque conteneur est joignable par son nom de service sur le réseau interne.

> **Alternative — Dev Services** : si on ne configurait *aucune* URL de base, Quarkus démarrerait tout seul un Postgres jetable en dev. Très pratique, mais « magique ». Ici on préfère l'explicite (`compose.yaml`) pour comprendre et contrôler.

> **Dépannage — `Bind for 0.0.0.0:5432 failed: port is already allocated`** : un autre processus occupe déjà le port 5432 (un PostgreSQL installé en natif, ou un vieux conteneur oublié — Docker Desktop redémarre parfois d'anciens conteneurs au lancement).
> - Diagnostiquer : `docker ps -a` (conteneurs existants) et `lsof -nP -iTCP:5432 -sTCP:LISTEN` (qui écoute sur le port).
> - **Solution A (rapide)** : mapper la base sur un port hôte libre, ex. `ports: ["5434:5432"]`, et pointer la datasource dessus (`jdbc:postgresql://localhost:5434/jobtrail`).
> - **Solution B (ménage)** : arrêter le processus qui squatte — `docker stop <nom_conteneur>` pour un conteneur, ou `brew services stop postgresql` pour un Postgres natif.

### Étape 2 — Ajouter les extensions de persistance

```bash
quarkus extension add hibernate-orm-panache jdbc-postgresql
```

- `hibernate-orm-panache` : Hibernate (ORM) + Panache (simplification).
- `jdbc-postgresql` : le **driver JDBC** PostgreSQL (connecteur bas niveau).

### Étape 3 — Configurer la connexion

> 📄 `backend/src/main/resources/application.properties`

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=jobtrail
quarkus.datasource.password=jobtrail
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/jobtrail

quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=true
```

- `database.generation=update` : Hibernate **fait évoluer** le schéma à partir des entités, en gardant les données. (`drop-and-create` recrée tout à chaque fois ; en prod on utilisera **Flyway**.)
- `log.sql=true` : affiche le SQL généré dans la console (pédagogique).

### Étape 4 — L'entité (active record)

> 📄 `backend/src/main/java/com/jobtrail/candidature/Candidature.java`
>
> ⚠️ **On ne crée pas un nouveau fichier** : on **remplace tout le contenu** du `Candidature.java` créé en P1 (le `record`) par cette entité. Même dossier, même nom, une seule classe `Candidature`.

```java
package com.jobtrail.candidature;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class Candidature extends PanacheEntity {
    public String entreprise;
    public String poste;
    public String statut;
}
```

- **`@Entity`** : mappée vers une table. Chaque champ = une colonne.
- **`extends PanacheEntity`** : fournit un `id` auto-généré **et** des méthodes prêtes (`listAll`, `findById`, `persist`, `deleteById`…). Pattern **« active record »** : l'entité sait se sauvegarder.
- Une entité **ne peut pas** être un `record` (JPA exige un constructeur sans argument et des champs modifiables).

### Étape 5 — Le CRUD complet

> 📄 `backend/src/main/java/com/jobtrail/candidature/CandidatureResource.java`

```java
package com.jobtrail.candidature;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/candidatures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CandidatureResource {

    @GET
    public List<Candidature> list() {
        return Candidature.listAll();
    }

    @GET @Path("/{id}")
    public Candidature findById(@PathParam("id") Long id) {
        Candidature c = Candidature.findById(id);
        if (c == null) throw new NotFoundException("Candidature " + id + " introuvable");
        return c;
    }

    @POST @Transactional
    public Response create(Candidature c) {
        c.persist();
        return Response.status(Response.Status.CREATED).entity(c).build();
    }

    @DELETE @Path("/{id}") @Transactional
    public void delete(@PathParam("id") Long id) {
        if (!Candidature.deleteById(id)) throw new NotFoundException("Candidature " + id + " introuvable");
    }
}
```

- **`@Transactional`** : obligatoire sur les écritures (transaction = tout réussit ou tout est annulé). Pas nécessaire en lecture.
- **`@PathParam("id")`** : lie le `{id}` de l'URL au paramètre.
- **`NotFoundException`** → Quarkus la convertit en **HTTP 404** automatiquement.
- **Pattern « repository »** (alternative à active record, plus proche de Spring Data) : une classe `implements PanacheRepository<Candidature>` injectée dans la ressource. Équivalent de `JpaRepository` chez Spring. On le présentera aussi.

### Étape 6 — Tester

```bash
curl -X POST http://localhost:8080/api/candidatures \
  -H "Content-Type: application/json" \
  -d '{"entreprise":"Stripe","poste":"Backend Java/Quarkus","statut":"CANDIDATE"}'

curl http://localhost:8080/api/candidatures
```

Regarde la console `quarkus dev` : tu verras le `create table`, l'`insert`, le `select` générés par Hibernate.

---

## Annexe — Stratégie Docker

Docker sert à **deux choses distinctes** :

1. **Les services dont dépend l'app** (PostgreSQL) → conteneurisés **dès le développement** (`compose.yaml`). Reproductible, isolé, proche de la prod.
2. **L'application elle-même** (back + front) → conteneurisée **pour le déploiement seulement** (P6).

**Pourquoi ne pas conteneuriser l'app pour développer ?** On perdrait le **live reload** (`quarkus dev`) et le **HMR** (`ng serve`) : il faudrait reconstruire une image à chaque modification → boucle de feedback lente.

> **Règle à retenir (et à dire en entretien)** : on conteneurise les *backing services* dès le dev, on développe l'app **en natif** contre ces services, et on ne produit l'**image de l'app** que pour le **déploiement** (Dockerfile multi-stage / Jib + orchestration `compose`/Kubernetes).

---

## Feuille de route

| Phase | Contenu | État |
|---|---|---|
| **P0** | Mise en place de l'environnement | ✅ |
| **P1** | Premier projet Quarkus + endpoint REST | ✅ |
| **P2** | PostgreSQL (Docker) + Hibernate Panache + CRUD | 🚧 en cours |
| **P3** | Sécurité (JWT, rôles, OWASP) | ⏳ |
| **P4** | Frontend Angular (login + liste) | ⏳ |
| **P5** | Frontend CRUD + tableau de bord | ⏳ |
| **P6** | DevOps cloud-native (Docker app, CI/CD, build natif) | ⏳ |
