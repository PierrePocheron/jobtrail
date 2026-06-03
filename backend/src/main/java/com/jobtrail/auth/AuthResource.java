package com.jobtrail.auth;

import com.jobtrail.user.Role;
import com.jobtrail.user.User;

import io.smallrye.jwt.build.Jwt;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

  @POST
  @Path("/register")
  @Transactional
  public Response resigster (@Valid Credentials creds) {
    if (User.findByUsername(creds.username()) != null) {
      throw new WebApplicationException("Utilisateur déjà existant", Response.Status.CONFLICT);
    }

    User user = new User();
    user.username = creds.username();
    user.passwordHash = BcryptUtil.bcryptHash(creds.password());
    user.role = Role.USER;
    user.persist();
    return Response.status(Response.Status.CREATED).build();
  }

  @POST
  @Path("/login")
  public Response login(@Valid Credentials creds) {
    User user = User.findByUsername(creds.username());

    if (user == null || !BcryptUtil.matches(creds.password(), user.passwordHash)) {
      throw new WebApplicationException("Identifiants invalides", Response.Status.UNAUTHORIZED);
    }

    String token = Jwt.issuer("https://jobtrail.dev/issuer")
                .upn(user.username)              // claim "upn" = le principal
                .groups(Set.of(user.role.name())) // claim "groups" = les rôles (RBAC), enum → String
                .expiresIn(Duration.ofHours(1))  // claim "exp" = expiration
                .sign();                         // signe en RS256 avec la clé privée

    return Response.ok(Map.of("token", token)).build();
  }
}
