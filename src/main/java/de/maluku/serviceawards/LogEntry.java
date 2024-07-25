package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class LogEntry implements Comparable<LogEntry> {
  @Builder.Default
  private UUID id = UUID.randomUUID();
  private String season;
  private Date date;
  private String title;
  private String notes;
  private String location;
  private int addServiceHours;
  private int addFullRewards;
  private int dm;
  private int prepTime;
  private int safetyTools;
  private int newPlayers;
  private int mentoring;
  private int reviewing;
  private int learnToPlay;
  private int codeOfConduct;
  private int streaming;
  private int eventOrga;
  private int eventStaffing;

  @Override
  public int compareTo(final LogEntry o) {
    if (this.date == null) return -1;
    if (o == null || o.date == null) return 1;
    return o.date.compareTo(this.date);
  }

  public int getTotalFullHours() {
    return addServiceHours + dm + prepTime + newPlayers + mentoring + streaming;
  }

  public int getTotalQuarterHours() {
    return safetyTools + 2*learnToPlay + 2*reviewing;
  }

  public ZonedDateTime getDateTime() {
    return date != null ? date.toInstant().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)) : null;
  }

  public String getTotalHours() {
    int quarts = getTotalQuarterHours();
    int hours = getTotalFullHours() + quarts / 4;
    quarts %= 4;
    return hours + (quarts == 1 ? "¼" : quarts == 2 ? "½"  : quarts == 3 ? "¾" : "");
  }

  public int getTotalRewards() {
    return addFullRewards + codeOfConduct + eventOrga + eventStaffing;
  }

  public static Stats getStats(String season, List<LogEntry> entries, List<Assigment> assigments) {
    Set<UUID> skipped = assigments.stream().filter(a->a.getEntryAsPlayer() != null && Objects.equals(season, a.getSeason())).map(Assigment::getEntryAsPlayer).collect(Collectors.toSet());
    return entries.stream().filter(l-> Objects.equals(l.getSeason(), season)).filter(l->!skipped.contains(l.getId())).map(Stats::new).reduce(Stats::add).orElse(new Stats());
  }
}

