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
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Assigment {
  private String season;
  private int reward;
  private UUID entryAsPlayer;
  private String rewardCharacter;
  private String levelUpCharacter;
  private Date assignedReward;
  private Date assignedLevel;
  private String title;
  private String note;
  boolean fullReward;

  public String description() {
    if (entryAsPlayer != null) {
      return "Entry:";
    }
    if (fullReward) {
      return "Full:";
    }
    return "Hours:";
  }
  public ZonedDateTime assignedRewardAt() {
    return assignedReward != null ? assignedReward.toInstant().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)) : null;
  }
  public ZonedDateTime assignedLevelAt() {
    return assignedLevel != null ? assignedLevel.toInstant().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)) : null;
  }
  public static Stats getStats(Season season, List<Assigment> assigments) {
    final String id = season.getId();
    final List<RewardEntry> rewards = season.getRewards();
    Stats stats = new Stats();
    for (final Assigment assigment : assigments) {
      if (!Objects.equals(assigment.getSeason(), id)) continue;
      if (assigment.getReward() < 0) continue;
      if (assigment.getReward() >= rewards.size()) continue;
      if (assigment.fullReward) stats = stats.add(new Stats(0, 1, 0));
      else stats = stats.add(new Stats(rewards.get(assigment.getReward())));
    }
    return stats;
  }
}


