package com.jobtrail;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/candidatures")
public class CandidatureResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<Candidature> listes(){
    return List.of(
      new Candidature(1L, "Google", "Backend java", "ENTRETIEN"),
      new Candidature(2L, "Doctolib", "Fullstack", "CANDIDATE"),
      new Candidature(3L, "Qonto", "Java/Quarkus", "REFUSE")
    );
  }
}
