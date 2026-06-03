package com.jobtrail.candidature;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/candidatures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CandidatureResource {

  @GET
  public List<Candidature> listes(){
    return Candidature.listAll();
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
    c.persist();
    return Response.status(Response.Status.CREATED).entity(c).build();
  }

  @DELETE
  @Path("/{id}")
  @Transactional
  public void delete(@PathParam("id") Long id) {
    if (!Candidature.deleteById(id)) throw new NotFoundException("Candidature " + id + " introuvable");
  }

}
