package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document
@Builder
@AllArgsConstructor
public class Season {
  @Id
  private String id;

  private Date start;
  private Date end;

  @Builder.Default
  private List<RewardEntry> rewards = new ArrayList<>();

  public boolean beforePrepTime() {
    return end.before(new GregorianCalendar(2023, Calendar.MARCH, 2).getTime());
  }

  public boolean beforeNewPlayers() {
    return end.before(new GregorianCalendar(2023, Calendar.MARCH, 2).getTime());
  }

  public boolean beforeReviewing() {
    return end.before(new GregorianCalendar(2022, Calendar.SEPTEMBER, 2).getTime());
  }
}
