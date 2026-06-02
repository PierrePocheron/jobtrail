package com.jobtrail.candidature;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class Candidature extends PanacheEntity{
  public String entreprise;
  public String poste;
  public String statut;
}
