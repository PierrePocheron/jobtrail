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
- [P3 — Sécurité (JWT, rôles, OWASP)](#p3--sécurité-jwt-rôles-owasp)
- [Annexe — Principes de conception](#annexe--principes-de-conception)
- [Annexe — Design patterns](#annexe--design-patterns)
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

### Étape 7 — Valider les entrées (« never trust user input »)

On ne fait **jamais** confiance aux données envoyées par le client : on valide **côté serveur**. On utilise **Hibernate Validator** (l'implémentation de la spec **Jakarta Bean Validation**). Côté Spring, c'est exactement le même moteur (`spring-boot-starter-validation` embarque aussi Hibernate Validator).

Ajouter l'extension, dans 📄 `backend/` :

```bash
quarkus extension add hibernate-validator
```

Annoter les champs de l'entité, 📄 `backend/src/main/java/com/jobtrail/candidature/Candidature.java` :

```java
package com.jobtrail.candidature;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Candidature extends PanacheEntity {

    @NotBlank(message = "L'entreprise est obligatoire")
    @Size(max = 120)
    public String entreprise;

    @NotBlank(message = "Le poste est obligatoire")
    @Size(max = 120)
    public String poste;

    @Size(max = 30)
    public String statut;
}
```

| Annotation | Vérifie que… |
|---|---|
| `@NotBlank` | la `String` n'est ni null, ni vide, ni que des espaces |
| `@NotNull` | la valeur n'est pas null (tout type) |
| `@Size(max = n)` | longueur ≤ n |

> Distinction d'entretien : `@NotNull` (pas null) ⊂ `@NotEmpty` (pas null + taille > 0) ⊂ `@NotBlank` (pas null + ≥ 1 caractère non-espace). Pour une `String` humaine → `@NotBlank`.

Déclencher la validation avec `@Valid` sur le paramètre du POST, 📄 `CandidatureResource.java` :

```java
import jakarta.validation.Valid;

@POST
@Transactional
public Response create(@Valid Candidature c) {
    c.persist();
    return Response.status(Response.Status.CREATED).entity(c).build();
}
```

C'est `@Valid` qui **active** la vérification. Sans lui, les contraintes sont ignorées sur cet endpoint. Si une contrainte échoue, Quarkus renvoie **automatiquement un HTTP 400** avec le détail des violations — aucun code d'erreur à écrire.

Tester un POST invalide (doit renvoyer **400**, pas 201) :

```bash
curl -i -X POST http://localhost:8080/api/candidatures \
  -H "Content-Type: application/json" \
  -d '{"entreprise":"","poste":"Dev","statut":"CANDIDATE"}'
```

---

## P3 — Sécurité (JWT, rôles, OWASP)

C'est **le** sujet clé pour un poste cloud-native. On construit une authentification **stateless** : le serveur ne retient aucune session, l'identité voyage dans un **token signé** (JWT) renvoyé à chaque requête.

### Vocabulaire

- **Authentification (authn)** : *« qui es-tu ? »* (login). **Autorisation (authz)** : *« as-tu le droit ? »* (rôles). Deux étapes distinctes.
- **Stateful (session) vs stateless (token)** : la session se stocke côté serveur ; le token, lui, est auto-porteur → le serveur ne retient rien, donc on **scale horizontalement** sans session partagée. C'est l'argument cloud-native.
- **Anatomie d'un JWT** : `header.payload.signature` (séparés par des points).
  - *header* : l'algorithme (ex. `RS256`).
  - *payload* : les **claims** (identité, rôles, expiration `exp`). Encodé en Base64, **pas chiffré** → lisible par tous : **jamais de secret dedans**.
  - *signature* : preuve cryptographique d'intégrité et d'origine.
- **Symétrique (HS256)** : une seule clé secrète signe ET vérifie. **Asymétrique (RS256)** : clé **privée** signe (secrète), clé **publique** vérifie (distribuable). Le standard cloud-native, qu'on utilise ici.

```
  eyJhbGci...  .  eyJ1cG4i...  .  Vx3kp9...
  └── header ──┘   └─ payload ─┘   └ signature ┘
       │               │                │
       │               │                └─ signée avec la clé PRIVÉE (RS256),
       │               │                   vérifiée avec la clé PUBLIQUE
       │               └─ claims : upn, groups, exp, iss…
       │                  (Base64 → LISIBLE par tous, PAS chiffré → aucun secret ici)
       └─ algorithme : {"alg":"RS256","typ":"JWT"}
```

### Approche retenue

JWT **émis par notre propre backend** via **SmallRye JWT** : entité `User`, mot de passe **haché BCrypt**, endpoint `/login` qui signe un JWT **RS256**, endpoints protégés par `@RolesAllowed`.

> Équivalent Spring : `spring-boot-starter-oauth2-resource-server` (vérification) + un `JwtEncoder` (émission) + `BCryptPasswordEncoder` (hash). Alternative « production » : déléguer l'identité à **Keycloak** via **OIDC** (`quarkus-oidc`), pattern à connaître et à citer en entretien.

### Schéma — le flux d'authentification

```mermaid
sequenceDiagram
    participant C as Client (Angular)
    participant API as API Quarkus
    participant DB as PostgreSQL

    Note over C,DB: Inscription (une fois)
    C->>API: POST /api/auth/register {username, password}
    API->>API: BcryptUtil.bcryptHash(password)
    API->>DB: INSERT app_user (passwordHash, role)
    API-->>C: 201 Created

    Note over C,DB: Connexion
    C->>API: POST /api/auth/login {username, password}
    API->>DB: SELECT app_user WHERE username
    API->>API: BcryptUtil.matches(password, hash)
    API->>API: Jwt...sign() — signe en RS256 (clé privée)
    API-->>C: 200 {token}

    Note over C,DB: Requête protégée
    C->>API: GET /api/candidatures<br/>Authorization: Bearer <token>
    API->>API: Vérifie signature (clé publique) + exp + iss
    API->>API: @RolesAllowed contrôle le claim "groups"
    API->>DB: SELECT candidatures
    API-->>C: 200 [données]
```

**Le point cloud-native à retenir** : à la dernière étape, l'API ne consulte **aucune session** ni la base pour authentifier — elle vérifie juste la **signature** du token avec la clé publique. C'est ça, le *stateless* : n'importe quelle instance derrière le load-balancer peut traiter la requête.

### Étape 1 — Extensions, clés RS256 et configuration

Ajouter les extensions, dans 📄 `backend/` :

```bash
quarkus extension add smallrye-jwt smallrye-jwt-build elytron-security-common
```

| Extension | Rôle | Équivalent Spring |
|---|---|---|
| `smallrye-jwt` | **vérifier** les JWT entrants + activer `@RolesAllowed` | `oauth2-resource-server` |
| `smallrye-jwt-build` | **émettre/signer** des JWT (le `/login`) | `JwtEncoder` |
| `elytron-security-common` | `BcryptUtil` pour **hacher les mots de passe** | `BCryptPasswordEncoder` |

Générer la paire de clés, dans 📄 `backend/src/main/resources/` :

```bash
cd src/main/resources

# 1. Clé privée (format PKCS#1)
openssl genrsa -out rsaPrivateKey.pem 2048
# 2. Conversion en PKCS#8 (format exigé par SmallRye JWT)
openssl pkcs8 -topk8 -nocrypt -inform PEM -in rsaPrivateKey.pem -out privateKey.pem
# 3. Extraction de la clé publique
openssl rsa -pubout -in rsaPrivateKey.pem -out publicKey.pem
# 4. Nettoyage
rm rsaPrivateKey.pem
```

> **Pourquoi PKCS#8 ?** `openssl genrsa` produit du PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`) ; SmallRye JWT attend du PKCS#8 (`-----BEGIN PRIVATE KEY-----`), un format d'encapsulation générique. Piège classique du « ma clé ne se charge pas ».

Configurer, dans 📄 `backend/src/main/resources/application.properties` :

```properties
# --- JWT : vérification des tokens entrants (smallrye-jwt) ---
mp.jwt.verify.publickey.location=publicKey.pem
mp.jwt.verify.issuer=https://jobtrail.dev/issuer

# --- JWT : signature des tokens émis (smallrye-jwt-build) ---
smallrye.jwt.sign.key.location=privateKey.pem
```

- `mp.jwt.*` = propriétés de la spec **MicroProfile JWT** (standard, `mp` = MicroProfile).
- L'**issuer** (`iss`) identifie l'émetteur : posé à l'émission, vérifié à la réception (un autre émetteur est rejeté).
- Les `.location` sont résolus dans le classpath (`src/main/resources/`).

> ⚠️ **Réflexe sécurité (OWASP — Cryptographic Failures).** On ne committe **jamais** une clé privée en prod : elle vient d'un secret manager (Vault, AWS Secrets Manager, K8s Secret) injecté à l'exécution. Sur ce projet d'apprentissage à clés jetables, ajoute au minimum `*.pem` à ton `.gitignore`.

> 💡 **OpenSSL 3.x** (macOS récent) génère déjà du PKCS#8 avec `genrsa` : l'étape de conversion devient alors un simple recopiage. Normal et sans conséquence — l'essentiel est que `privateKey.pem` soit bien en `-----BEGIN PRIVATE KEY-----`.

### Étape 2 — Entité `User`, hash BCrypt, inscription

**L'entité**, 📄 `backend/src/main/java/com/jobtrail/user/User.java` (nouveau package `user/`) :

```java
package com.jobtrail.user;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public String username;

    @Column(nullable = false)
    public String passwordHash;   // jamais le mot de passe en clair

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;             // enum USER / ADMIN (type fermé, pas de String libre)

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
```

Le rôle est un **enum** dédié, 📄 `backend/src/main/java/com/jobtrail/user/Role.java` :

```java
package com.jobtrail.user;

public enum Role {
    USER,
    ADMIN
}
```

- **`@Table(name = "app_user")`** : `user` est un **mot réservé** PostgreSQL → on renomme la table pour éviter l'échec du `create table`.
- **`passwordHash`** : on stocke l'**empreinte BCrypt**, jamais le mot de passe en clair (OWASP — *Cryptographic Failures*).
- **`enum Role` + `@Enumerated(EnumType.STRING)`** : un enum = *type fermé* (seules les valeurs valides existent, erreurs attrapées à la compilation). ⚠️ **Toujours `EnumType.STRING`** : par défaut JPA stocke l'**ordinal** (l'index `0,1,…`) → réordonner l'enum corromprait les données existantes. `STRING` stocke le nom (`"USER"`), robuste et lisible en base.
- **`findByUsername`** : requête Panache, `find("username", x)` → `WHERE username = ?`, `.firstResult()` → 1er ou `null`.

**Le DTO d'identifiants** (on n'expose pas l'entité brute), 📄 `backend/src/main/java/com/jobtrail/auth/Credentials.java` :

```java
package com.jobtrail.auth;

import jakarta.validation.constraints.NotBlank;

public record Credentials(
    @NotBlank String username,
    @NotBlank String password
) {}
```

**L'endpoint d'inscription**, 📄 `backend/src/main/java/com/jobtrail/auth/AuthResource.java` :

```java
package com.jobtrail.auth;

import com.jobtrail.user.Role;
import com.jobtrail.user.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @POST
    @Path("/register")
    @Transactional
    public Response register(@Valid Credentials creds) {
        if (User.findByUsername(creds.username()) != null) {
            throw new WebApplicationException("Utilisateur déjà existant", Response.Status.CONFLICT);
        }
        User user = new User();
        user.username = creds.username();
        user.passwordHash = BcryptUtil.bcryptHash(creds.password());  // hash + sel automatique
        user.role = Role.USER;
        user.persist();
        return Response.status(Response.Status.CREATED).build();
    }
}
```

- **`BcryptUtil.bcryptHash(...)`** : BCrypt intègre un **sel aléatoire** → deux fois le même mot de passe donnent deux empreintes différentes. (Spring : `passwordEncoder.encode(...)`.)
- **`WebApplicationException(..., CONFLICT)`** → HTTP **409**, convertie automatiquement par Quarkus.

> ⚠️ **Nuance sécurité — l'énumération d'utilisateurs (*user enumeration*).** Ce `409` révèle qu'un username existe déjà — ce qui contredit l'effort anti-énumération fait sur le `/login`. C'est un **arbitrage conscient**, à raisonner selon la nature de l'identifiant :
> - **Username public** (pseudo type GitHub/Twitter) : l'info est déjà exposée → renvoyer `409` est acceptable, et meilleur pour l'UX (« ce pseudo est pris »). **C'est notre cas dans JobTrail.**
> - **Email / identifiant privé** : révéler l'existence d'un compte est un vrai risque (phishing, credential stuffing). La parade de référence est la **confirmation hors-bande** : on répond **toujours** `200 / « vérifie tes emails »`, et c'est l'email envoyé au **vrai** propriétaire qui distingue les cas — l'info ne fuit jamais dans la réponse HTTP. À compléter par du **rate limiting**.
>
> En entretien, le bon réflexe est de **nommer le compromis**, pas de l'ignorer.

### Étape 3 — `/login` : émettre un JWT signé

On ajoute `login` dans le **même** `AuthResource`. Imports à compléter en haut :

```java
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
```

L'**issuer** est défini une seule fois dans `application.properties` ; on l'injecte ici plutôt que de le réécrire en dur. Champ à ajouter dans la classe :

```java
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;
```

> **`@ConfigProperty`** = l'équivalent Quarkus/MicroProfile de `@Value("${...}")` chez Spring : il injecte une valeur de configuration dans un champ. L'issuer a ainsi **une seule source de vérité**, partagée entre l'émission (ce champ) et la vérification (`mp.jwt.verify.issuer`).

#### Au passage — le principe DRY (*Don't Repeat Yourself*)

**DRY = « ne te répète pas ».** Chaque information ou règle ne doit avoir qu'**une seule source de vérité** (*single source of truth*) dans le code. Dès qu'une même valeur est dupliquée à deux endroits, tu crées une dette : le jour où tu modifies l'une sans penser à l'autre, les deux **divergent** → bug souvent **silencieux** (aucune erreur de compilation) et pénible à traquer.

Le cas concret ici : l'issuer existait en dur dans `login()` **et** dans `application.properties`. Deux copies → changer l'une casse l'auth sans prévenir. En centralisant la valeur dans la config et en l'injectant via `@ConfigProperty`, il ne reste qu'**un seul endroit** à modifier.

Comment appliquer DRY en pratique : extraire une **constante**, une **méthode/fonction** partagée, **externaliser la configuration**, ou utiliser héritage/composition. *(Parallèle front que tu connais : sortir une valeur magique dans une `const` partagée, ou factoriser une logique répétée dans un hook/utilitaire.)*

> ⚠️ **La nuance à connaître (et à dire en entretien) :** DRY concerne la **connaissance**, pas le code qui *se ressemble* visuellement. Deux bouts de code identiques par **coïncidence** — qui évolueront ensuite pour des raisons différentes — ne doivent **pas** forcément être fusionnés : sur-factoriser crée un **couplage artificiel** parfois pire que la duplication. La règle empirique est la **« règle de trois »** : on factorise quand la même chose apparaît une **3ᵉ** fois, pas avant.

La méthode :

```java
    @POST
    @Path("/login")
    public Response login(@Valid Credentials creds) {
        User user = User.findByUsername(creds.username());

        // Même message dans les deux cas : on ne révèle pas si le compte existe
        if (user == null || !BcryptUtil.matches(creds.password(), user.passwordHash)) {
            throw new WebApplicationException("Identifiants invalides", Response.Status.UNAUTHORIZED);
        }

        String token = Jwt.issuer(issuer)           // valeur lue depuis application.properties
                .upn(user.username)                 // claim "upn" = le principal
                .groups(Set.of(user.role.name()))   // claim "groups" = les rôles (RBAC), enum → String
                .expiresIn(Duration.ofHours(1))     // claim "exp" = expiration
                .sign();                            // signe en RS256 avec la clé privée

        return Response.ok(Map.of("token", token)).build();
    }
```

> **L'issuer (`iss`) n'est pas une adresse appelée** : c'est juste un identifiant unique. Le format URL est une convention OAuth2/OIDC (garantit l'unicité), mais `https://jobtrail.dev/issuer` n'a pas besoin d'exister ni d'être joignable. Seule règle : valeur identique à l'émission et à la vérification. *(En OIDC réel via Keycloak, là l'issuer est une vraie URL qui publie clés et métadonnées.)*

- **`BcryptUtil.matches(clair, hash)`** : re-hache l'entrée avec le sel stocké et compare. (Spring : `passwordEncoder.matches(...)`.)
- **`Jwt.…sign()`** (de `smallrye-jwt-build`) construit et **signe le JWT en RS256** avec `privateKey.pem`. Aucune crypto manuelle.
- Claims posés : `iss` (doit matcher la vérif), `upn` (principal), `groups` (rôles → lus par `@RolesAllowed`), `exp` (expiration courte). `iat`/`jti` ajoutés automatiquement.
- **Anti-énumération (OWASP)** : « user inconnu » et « mauvais mot de passe » → **même 401**, même message. On ne révèle pas quels comptes existent.

**Tester :**

```bash
# Inscription → 201
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"pierre","password":"secret123"}'

# Login → 200 + {"token":"eyJ..."}
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"pierre","password":"secret123"}'
```

Colle le token sur **https://jwt.io** : header (`RS256`) + payload (claims en clair) → preuve que le payload est **encodé, pas chiffré**.

---

## Annexe — Principes de conception

Ces principes ne sont pas propres à Java : ce sont des **règles de bon sens partagées** par tous les bons développeurs, et un **vocabulaire d'entretien** très attendu. On en cite un (DRY) en détail dans [P3](#au-passage--le-principe-dry-dont-repeat-yourself) ; voici les autres incontournables, avec leur ancrage dans **JobTrail**.

### Les principes généraux

| Principe | En une phrase | Pourquoi / piège | Dans JobTrail |
|---|---|---|---|
| **DRY** — *Don't Repeat Yourself* | Chaque règle a **une seule source de vérité**. | Duplication → divergence silencieuse. ⚠️ S'applique à la *connaissance*, pas au code qui se ressemble par hasard (règle de trois). | issuer centralisé via `@ConfigProperty` |
| **KISS** — *Keep It Simple, Stupid* | La solution la plus **simple** qui marche est la meilleure. | La complexité est une dette : plus dur à lire, tester, débugger. Pas de « au cas où ». | Panache (active record) plutôt qu'une couche DAO maison |
| **YAGNI** — *You Aren't Gonna Need It* | N'écris que ce dont tu as besoin **maintenant**. | On sur-anticipe des besoins futurs qui n'arrivent jamais → code mort. Cousin de KISS. | on n'a pas ajouté de cache/pagination « pour plus tard » |
| **SoC** — *Separation of Concerns* | Chaque module a **une préoccupation** et une seule. | Mélanger logique métier, accès données et HTTP rend tout rigide. | couches Resource / Service / Repository ; entité ≠ DTO |
| **Fail Fast** | Détecter et **rejeter une erreur au plus tôt**. | Une donnée invalide qui se propage cause des bugs lointains et obscurs. | validation `@Valid` qui renvoie 400 dès l'entrée |
| **Least Privilege** (sécurité) | Donner le **minimum de droits** nécessaire. | Limite les dégâts en cas de compromission (OWASP). | `@RolesAllowed` : chaque endpoint n'autorise que les rôles requis |
| **Composition over Inheritance** | Préférer **assembler** des objets plutôt qu'empiler l'héritage. | L'héritage profond est rigide et fragile ; la composition est souple. | services **injectés** (CDI) plutôt qu'une hiérarchie de classes |

### SOLID — les 5 piliers de la POO

L'acronyme le plus demandé en entretien Java. Cinq principes pour un code orienté objet maintenable :

| Lettre | Principe | Idée | Exemple parlant |
|---|---|---|---|
| **S** | *Single Responsibility* | Une classe = **une seule raison de changer**. | `CandidatureResource` gère l'HTTP, pas les règles métier ni le SQL. |
| **O** | *Open/Closed* | **Ouvert** à l'extension, **fermé** à la modification. | Ajouter un comportement via une nouvelle classe/implémentation, sans réécrire l'existant. |
| **L** | *Liskov Substitution* | Un sous-type doit pouvoir **remplacer** son parent sans casser le code. | Toute implémentation d'une interface doit respecter son contrat (pas d'exception surprise). |
| **I** | *Interface Segregation* | Plusieurs **petites interfaces** ciblées valent mieux qu'une grosse fourre-tout. | Un client ne dépend que des méthodes qu'il utilise vraiment. |
| **D** | *Dependency Inversion* | Dépendre d'**abstractions**, pas d'implémentations concrètes. | C'est **exactement** l'injection de dépendances : `@Inject MonService` (CDI Quarkus) ou `@ConfigProperty` — le code reçoit ses dépendances, ne les fabrique pas. |

> **Le point qui impressionne en entretien :** le **D** de SOLID (*Dependency Inversion*) est la justification théorique de l'**injection de dépendances** que tu utilises partout en Quarkus (CDI) comme en Spring. Quand on te demande « pourquoi de l'injection de dépendances ? », la réponse est : *« pour dépendre d'abstractions et non d'implémentations → code découplé, testable (on injecte un mock), et conforme au principe d'inversion des dépendances »*.

> **À retenir globalement :** ces principes sont des **guides, pas des dogmes**. Ils tirent tous dans le même sens — du code **simple, découplé, à responsabilité unique, sans duplication** — mais se contredisent parfois (sur-appliquer DRY peut violer KISS). Le métier, c'est l'**arbitrage** : savoir lequel prime selon le contexte. C'est précisément ce qu'un recruteur cherche à entendre.

---

## Annexe — Design patterns

Un **design pattern** est une **solution éprouvée à un problème de conception récurrent** — un « modèle » qu'on adapte, pas du code à copier-coller. À distinguer des [principes](#annexe--principes-de-conception) : un principe est une *règle* (« ne te répète pas »), un pattern est une *recette* (« voici comment structurer ce cas précis »).

Les plus connus viennent du livre du **« Gang of Four » (GoF)**, classés en 3 familles : **créationnels** (comment créer des objets), **structurels** (comment les assembler), **comportementaux** (comment ils interagissent).

> **Le réflexe d'entretien le plus important :** ne **jamais** plaquer un pattern « pour faire bien ». Un pattern inutile = sur-ingénierie (violation de KISS/YAGNI). On les introduit quand le problème qu'ils résolvent apparaît *réellement*.

### Ceux que tu utilises DÉJÀ dans JobTrail

C'est ça qui fait mouche en entretien : pouvoir dire *« je l'ai utilisé ici »*.

| Pattern | Famille | Où, dans le projet |
|---|---|---|
| **Builder** | Créationnel | `Jwt.issuer(...).upn(...).groups(...).sign()` : on construit un objet complexe étape par étape via une API *fluent*. (Aussi `Response.status(...).entity(...).build()`.) |
| **Singleton** | Créationnel | Tes beans CDI `@ApplicationScoped` : **une seule instance** partagée par toute l'app, gérée par le conteneur (pas de `new`). |
| **Dependency Injection / IoC** | (architectural) | `@Inject`, `@ConfigProperty` : le **conteneur** (CDI Quarkus / Spring) crée et fournit les dépendances → *Inversion of Control*. C'est l'application concrète du **D de SOLID**. |
| **Repository** | (entreprise) | `PanacheRepository<Candidature>` : abstrait l'accès aux données derrière une interface. Équivalent `JpaRepository` Spring. |
| **Active Record** | (entreprise) | `Candidature extends PanacheEntity` : l'entité **sait se sauvegarder** (`persist()`, `findById()`). Alternative au Repository. |
| **DTO** — *Data Transfer Object* | (entreprise) | `Credentials` : un objet dédié au **transport** des données d'API, distinct de l'entité. |
| **Proxy** | Structurel | `@Transactional` : Quarkus enveloppe ta méthode dans un **proxy** qui ouvre/valide la transaction autour de ton code, sans que tu l'écrives. |

### Les autres grands classiques à connaître

| Pattern | Famille | Idée | Cas d'usage typique |
|---|---|---|---|
| **Factory** | Créationnel | Déléguer la création d'objets à une méthode/classe dédiée. | Choisir l'implémentation à créer selon un paramètre. |
| **Strategy** | Comportemental | Encapsuler des algorithmes interchangeables derrière une interface commune. | Plusieurs méthodes de paiement, de tri, de calcul de prix. |
| **Observer** | Comportemental | Des objets s'**abonnent** à un événement et sont notifiés. | Événements CDI (`@Observes`), systèmes pub/sub. *(Tu connais : c'est la logique des listeners JS.)* |
| **Adapter** | Structurel | Faire dialoguer deux interfaces incompatibles. | Brancher une lib externe sur ton propre contrat. |
| **Facade** | Structurel | Exposer une **interface simple** par-dessus un sous-système complexe. | Une classe `Service` qui masque plusieurs appels. |
| **Decorator** | Structurel | Ajouter un comportement à un objet **sans modifier sa classe**. | Ajouter logging/cache autour d'un service. |
| **Template Method** | Comportemental | Une classe parente fixe le **squelette** d'un algorithme, les enfants remplissent les trous. | Étapes communes + variations par sous-classe. |

> **MVC** (Modèle-Vue-Contrôleur) est un pattern d'**architecture** plus large : côté back il se reflète dans le découpage **Entity (modèle) / Resource (contrôleur)**, la « vue » étant le JSON (ou l'app Angular). À ne pas confondre avec les patterns GoF, plus fins.

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
| **P2** | PostgreSQL (Docker) + Hibernate Panache + CRUD + validation | ✅ |
| **P3** | Sécurité (JWT, rôles, OWASP) | 🚧 en cours |
| **P4** | Frontend Angular (login + liste) | ⏳ |
| **P5** | Frontend CRUD + tableau de bord | ⏳ |
| **P6** | DevOps cloud-native (Docker app, CI/CD, build natif) | ⏳ |
