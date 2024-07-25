package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class RewardEntry {
  private String name;
  private int downtime;
  private int gp;

  private String url;
  private String description;

  private RewardLevel level;

  private boolean allowsLevel;
  private boolean repeatable;
}
