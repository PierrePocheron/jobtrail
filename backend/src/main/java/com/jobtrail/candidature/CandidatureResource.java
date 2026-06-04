package com.jobtrail.candidature;

import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/candidatures")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CandidatureResource {

  @Inject
  JsonWebToken jwt;

  @GET
  public List<Candidature> list(){
    return Candidature.list("owner", jwt.getName());
  }

  @GET
  @Path("/{id}")
  public Candidature byId(@PathParam("id") Long id) {
    Candidature c = Candidature.findById(id);
    if (c == null) throw new NotFoundException("Candidature " + id + " introuvable");
    return c;
  }

  @POST
  @Transactional
  public Response create(@Valid Candidature c){
    c.owner = jwt.getName();
    c.persist();
    return Response.status(Response.Status.CREATED).entity(c).build();
  }

  @DELETE
  @Path("/{id}")
  @RolesAllowed("ADMIN")
  @Transactional
  public void delete(@PathParam("id") Long id) {
    if (!Candidature.deleteById(id)) throw new NotFoundException("Candidature " + id + " introuvable");
  }

}
