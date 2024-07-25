package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class RewardLevel {
  @Id
  private String id;
  private String name;
  private int cost;
  private boolean singleSession;
}
