package com.jobtrail.candidature;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CandidatureResourceTest {

  @Test
  @DisplayName("Sans token, l'accès aux données est refusé (401)")
  void sansToken_renvoie401() {
    given()
    .when().get("/api/candiatures")
    .then().statusCode(401);
  }
}