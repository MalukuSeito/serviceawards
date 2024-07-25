package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Document
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class User {
  @Id
  @NonNull
  private String userId;
  @Builder.Default
  private List<LogEntry> logEntries = new ArrayList<>(
    //List.of(new LogEntry("0","11A", new Date(), "A", "B", "Home", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))
  );
  @Builder.Default
  private List<Assigment> assignments = new ArrayList<>();

  private boolean admin;

  public List<Assigment> find(String season, int index) {
    return assignments.stream().filter(a-> Objects.equals(a.getSeason(), season) && a.getReward() == index).toList();
    /*return List.of(Assigment.builder()
        .title("+2 Sword")
        .assignedLevel(new java.util.Date())
        .levelUpCharacter("Wither")
        .assignedReward(new java.util.Date())
        .rewardCharacter("Kara the Barbarian")
        .note("A Note with multiple\nlines<i> Meh</i>")
      .build());*/
  }
  public LogEntry find(UUID id) {
    return logEntries.stream().filter(a-> Objects.equals(a.getId(), id)).findFirst().orElse(null);
  }

  public boolean used(UUID id) {
    return assignments.stream().anyMatch(s->Objects.equals(s.getEntryAsPlayer(), id));
  }

  public List<LogEntry> available(String season, UUID... alwaysInclude) {
    Set<UUID> assigned = assignments.stream().filter(s->s.getEntryAsPlayer() != null && Objects.equals(season, s.getSeason())).map(Assigment::getEntryAsPlayer).collect(Collectors.toSet());
    Arrays.asList(alwaysInclude).forEach(assigned::remove);
    return logEntries.stream().filter(l->Objects.equals(l.getSeason(), season)).filter(l->!assigned.contains(l.getId())).toList();
  }
  public List<String> locations() {
    return logEntries.stream().map(LogEntry::getLocation).sorted().distinct().toList();
  }

  public List<String> characters() {
    return assignments.stream().flatMap(a-> Stream.of(a.getRewardCharacter(), a.getLevelUpCharacter())).filter(Objects::nonNull).sorted().distinct().toList();
  }
}
