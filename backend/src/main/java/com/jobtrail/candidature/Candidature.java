package com.jobtrail.candidature;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Candidature extends PanacheEntity{

  @NotBlank(message = "L'entreprise est obligatoire")
  @Size(max = 120)
  public String entreprise;


  @NotBlank(message = "Le poste est obligatoire")
  @Size(max = 120)
  public String poste;

  @Size(max = 30)
  public String statut;
}
