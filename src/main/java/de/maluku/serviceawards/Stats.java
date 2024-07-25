package de.maluku.serviceawards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@ToString
public class Stats {
  private int servicehours;
  private int rewards;
  private int quarters;

  public Stats(LogEntry entry) {
    this.servicehours = entry.getTotalFullHours() + entry.getTotalQuarterHours() / 4;
    this.rewards = entry.getTotalRewards();
    this.quarters = entry.getTotalQuarterHours() % 4;
  }

  public Stats(final RewardEntry rewardEntry) {
    this(rewardEntry != null ? rewardEntry.getLevel() : null);
  }

  public Stats(final RewardLevel rewardLevel) {
    this.quarters = 0;
    if (rewardLevel != null) {
      this.servicehours = rewardLevel.getCost();
      this.rewards = 0;
    } else {
      this.servicehours = 0;
      this.rewards = 0;
    }
  }

  public Stats add(Stats other) {
    int quarters = this.quarters + other.getQuarters();
    return new Stats(this.servicehours + other.getServicehours() + quarters / 4, this.rewards + other.getRewards(), quarters % 4);
  }
}
