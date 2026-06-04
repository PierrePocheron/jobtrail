package com.jobtrail.candidature;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.JwtSecurity;

@QuarkusTest
class CandidatureResourceTest {

  @Test
  @DisplayName("Sans token, l'accès aux données est refusé (401)")
  void sansToken_renvoie401() {
    given()
    .when().get("/api/candiatures")
    .then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "pierre", roles = "USER")
  @JwtSecurity(claims = @io.quarkus.test.security.jwt.Claim(key = "upn", value = "pierre"))
  @DisplayName("Un USER authentifié peut lister ses candidatures (200)")
  void userAuthentifie_peutLister(){
    given()
      .when().get("/api/candidatures")
      .then().statusCode(200);
  }

  @Test
  @TestSecurity(user = "pierre", roles = "USER")
  @DisplayName("Un USER ne peut pas supprimer (403, réservé ADMIN)")
  void user_nePeutPasSupprimer() {
      given()
          .when().delete("/api/candidatures/1")
          .then().statusCode(403);   // autorisé authentifié, mais rôle insuffisant
  }

  @Test
  @TestSecurity(user = "boss", roles = "ADMIN")
  @DisplayName("Un ADMIN peut supprimer")
  void admin_peutSupprimer() {
      given()
          .when().delete("/api/candidatures/999")  // id inexistant
          .then().statusCode(404);   // accès autorisé : on passe le RBAC, l'objet n'existe pas
  }
}