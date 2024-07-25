package de.maluku.serviceawards;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;

@Controller
@AllArgsConstructor
public class ServiceController {

  private final SeasonRepository seasonRepository;
  private final RewardLevelRepository rewardLevelRepository;
  private final UserRepository userRepository;

  @GetMapping("/")
  public String home(Principal principal, Model model) {
    if (principal != null) {
      return "redirect:/log/" + principal.getName();
    }
    return "home";
  }

  @GetMapping("/admin")
  public String admin(Model model) {
    testAdmin();
    model.addAttribute("seasons",seasonRepository.findAll());
    model.addAttribute("levels", rewardLevelRepository.findAll());
    model.addAttribute("offset", 0);
    Season latest = seasonRepository.findFirstByOrderByStartDesc();
    if (latest != null) {
      model.addAttribute(latest);
    }
    return "admin";
  }

  private User getUser(String uid) {
    Optional<User> user = userRepository.findById(uid);
    if (user.isPresent()) {
      return user.get();
    }
     Principal principal = SecurityContextHolder.getContext().getAuthentication();
    if (principal != null && !(principal instanceof AnonymousAuthenticationToken) && principal.getName().equals(uid)) {
      return new User(uid);
    }
    throw new ResponseStatusException(NOT_FOUND);
  }

  @GetMapping("/log/{uid}")
  public String log(@PathVariable String uid, Model model) {
    User user = getUser(uid);
    model.addAttribute("seasons",seasonRepository.findAll());
    model.addAttribute("offset", 0);
    model.addAttribute("edit", null);
    model.addAttribute("add", -1);
    model.addAttribute("user", user);
    model.addAttribute("locations", user.locations());
    model.addAttribute("characters", user.characters());
    model.addAttribute("rewardLevels", rewardLevelRepository.findAll());
    Season latest = seasonRepository.findFirstByOrderByStartDesc();
    if (latest != null) {
      model.addAttribute("season", latest);
      model.addAttribute("assignableLogs", user.available(latest.getId()));
      model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
    } else {
      model.addAttribute("assignableLogs", Collections.emptyList());
      model.addAttribute("earned", new Stats());
      model.addAttribute("used", new Stats());
    }
    return "overview";
  }

  private User ensureLogin(User user) {
    Principal principal = SecurityContextHolder.getContext().getAuthentication();
    if (principal != null && !(principal instanceof AnonymousAuthenticationToken) && principal.getName().equals(user.getUserId())) {
      return user;
    }
    throw new ResponseStatusException(FORBIDDEN);
  }

  @PostMapping("/log/{uid}/{id}/{index}")
  public String assign(
    @PathVariable String uid,
    @PathVariable String id,
    @PathVariable int index,
    @RequestParam(required = false) String title,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime assignedReward,
    @RequestParam(required = false) String rewardCharacter,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime assignedLevel,
    @RequestParam(required = false) String levelUpCharacter,
    @RequestParam(required = false) String note,
    @RequestParam(required = false) String entryAsPlayer,
    @RequestParam(defaultValue = "false") boolean fullRewards,
    @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    Optional<Season> latest = seasonRepository.findById(id);
    if (latest.isEmpty() || index < 0 || index >= latest.get().getRewards().size()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    UUID entry = entryAsPlayer != null && !entryAsPlayer.isEmpty() ? UUID.fromString(entryAsPlayer) : null;
    Assigment assigment = Assigment.builder()
      .note(note)
      .levelUpCharacter(levelUpCharacter)
      .rewardCharacter(rewardCharacter)
      .assignedReward(assignedReward != null ? Date.from(assignedReward.toInstant(ZoneOffset.UTC)) : null)
      .assignedLevel(assignedLevel != null ? Date.from(assignedLevel.toInstant(ZoneOffset.UTC)) : null)
      .entryAsPlayer(entry)
      .reward(index)
      .title(title)
      .fullReward(fullRewards)
      .season(id)
      .build();

    user.getAssignments().add(assigment);
    user = userRepository.save(user);
    RewardEntry reward = latest.get().getRewards().get(index);
    if (htmx) {
      model.addAttribute("offset", index);
      model.addAttribute("edit", null);
      model.addAttribute("add", reward.isRepeatable() ? index : -1);
      model.addAttribute("season", Season.builder().id(latest.get().getId()).rewards(List.of(reward)).build());
      model.addAttribute("savedAssignment", true);
      model.addAttribute("characters", user.characters());
      model.addAttribute("earned", LogEntry.getStats(latest.get().getId(), user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", Assigment.getStats(latest.get(), user.getAssignments()));
      model.addAttribute("assignableLogs", user.available(latest.get().getId()));
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id + (reward.isRepeatable() ? "/" + index : ""));
      response.addHeader("HX-Trigger", "logsChanged");
      if (entry != null) {
        response.addHeader("HX-Trigger-After-Settle", "UpdateLog"+entry);
      }
      model.addAttribute("user", user);
      return "overview :: reward";
    }
    else {
      return "redirect:/log/" + uid + '/' + id + (reward.isRepeatable() ? "/" + index : "");
    }
  }

  @PostMapping("/log/{uid}/{id}/{reward}/{index}")
  public String updateAssign(
    @PathVariable String uid,
    @PathVariable String id,
    @PathVariable int reward,
    @PathVariable int index,
    @RequestParam(required = false) String title,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime assignedReward,
    @RequestParam(required = false) String rewardCharacter,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime assignedLevel,
    @RequestParam(required = false) String levelUpCharacter,
    @RequestParam(required = false) String note,
    @RequestParam(required = false) String entryAsPlayer,
    @RequestParam(defaultValue = "false") boolean fullRewards,
    @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    Optional<Assigment> a = user.getAssignments().stream().filter(aa-> Objects.equals(id, aa.getSeason()) && reward == aa.getReward()).skip(index).findFirst();
    Optional<Season> latest = seasonRepository.findById(id);
    if (latest.isEmpty() || index < 0 || index >= latest.get().getRewards().size()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    if (a.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find assignment");
    }
    UUID entry = entryAsPlayer != null && !entryAsPlayer.isEmpty() ? UUID.fromString(entryAsPlayer) : null;
    Assigment assigment = a.get();
    UUID oldEntry = assigment.getEntryAsPlayer();
    assigment.setNote(note);
    assigment.setLevelUpCharacter(levelUpCharacter);
    assigment.setEntryAsPlayer(entry);
    assigment.setFullReward(fullRewards);
    assigment.setTitle(title);
    assigment.setRewardCharacter(rewardCharacter);
    assigment.setAssignedLevel(assignedLevel != null ? Date.from(assignedLevel.toInstant(ZoneOffset.UTC)) : null);
    assigment.setAssignedReward(assignedReward != null ? Date.from(assignedReward.toInstant(ZoneOffset.UTC)) : null);

    user = userRepository.save(user);
    RewardEntry rewardEntry = latest.get().getRewards().get(reward);
    if (htmx) {
      model.addAttribute("offset", reward);
      model.addAttribute("edit", null);
      model.addAttribute("add", rewardEntry.isRepeatable() ? reward : -1);
      model.addAttribute("season", Season.builder().id(latest.get().getId()).rewards(List.of(rewardEntry)).build());
      model.addAttribute("savedAssignment", true);
      model.addAttribute("characters", user.characters());
      model.addAttribute("earned", LogEntry.getStats(latest.get().getId(), user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", Assigment.getStats(latest.get(), user.getAssignments()));
      model.addAttribute("assignableLogs", user.available(latest.get().getId()));
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id + (rewardEntry.isRepeatable() ? "/" + reward : ""));
      response.addHeader("HX-Trigger", "logsChanged");
      if (!Objects.equals(oldEntry, entry)) {
        List<String> events = new ArrayList<>();
        if (oldEntry != null) events.add("UpdateLog"+oldEntry);
        if (entry != null) events.add("UpdateLog"+entry);
        response.addHeader("HX-Trigger-After-Settle", String.join(",", events));
      }
      model.addAttribute("user", user);
      return "overview :: reward";
    }
    else {
      return "redirect:/log/" + uid + '/' + id + (rewardEntry.isRepeatable() ? "/" + reward : "");
    }
  }

  @PostMapping("/entry/{uid}/{id}")
  public String update(
    @PathVariable String uid,
    @PathVariable String id,
    @RequestParam(required = false) String title,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
    @RequestParam(required = false) String location,
    @RequestParam(required = false) String notes,
    @RequestParam(defaultValue = "0") int dm,
    @RequestParam(defaultValue = "0") int prep,
    @RequestParam(defaultValue = "0") int safetyTools,
    @RequestParam(defaultValue = "0") int newPlayers,
    @RequestParam(defaultValue = "0") int mentoring,
    @RequestParam(defaultValue = "0") int learnToPlay,
    @RequestParam(defaultValue = "0") int reviewing,
    @RequestParam(defaultValue = "0") int codeOfConduct,
    @RequestParam(defaultValue = "0") int eventOrga,
    @RequestParam(defaultValue = "0") int staffing,
    @RequestParam(defaultValue = "0") int streaming,
    @RequestParam(defaultValue = "0") int addHours,
    @RequestParam(defaultValue = "0") int addRewards,
    @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> e = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (e.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    LogEntry entry = e.get();
    Season latest = seasonRepository.findById(entry.getSeason()).orElse(seasonRepository.findFirstByOrderByStartDesc());
    entry.setDate(date != null ? Date.from(date.toInstant(ZoneOffset.UTC)) : null);
    entry.setTitle(title);
    entry.setAddFullRewards(addRewards);
    entry.setAddServiceHours(addHours);
    entry.setCodeOfConduct(codeOfConduct);
    entry.setNotes(notes);
    entry.setDm(dm);
    entry.setPrepTime(prep);
    entry.setSafetyTools(safetyTools);
    entry.setNewPlayers(newPlayers);
    entry.setMentoring(mentoring);
    entry.setLearnToPlay(learnToPlay);
    entry.setReviewing(reviewing);
    entry.setEventOrga(eventOrga);
    entry.setStreaming(streaming);
    entry.setEventStaffing(staffing);
    entry.setLocation(location);
    user = userRepository.save(user);
    if (htmx) {
      model.addAttribute("offset", 0);
      model.addAttribute("edit", null);
      model.addAttribute("add", -1);
      model.addAttribute("season", latest);
      model.addAttribute("saved", true);
      model.addAttribute("locations", user.locations());
      model.addAttribute("characters", user.characters());
      model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
      model.addAttribute("assignableLogs", user.available(latest.getId()));
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id);
      response.addHeader("HX-Trigger", "logsChanged");
      model.addAttribute("user", User.builder().userId(user.getUserId()).logEntries(List.of(entry)).build());
      return "overview :: log";
    }
    else {
      return "redirect:/log/" + uid + '/' + latest.getId();
    }

  }

  @PostMapping("/log/{uid}/{id}")
  public String newLog(
    @PathVariable String uid,
    @PathVariable String id,
    @RequestParam(required = false) String title,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
    @RequestParam(required = false) String location,
    @RequestParam(required = false) String notes,
    @RequestParam(defaultValue = "0") int dm,
    @RequestParam(defaultValue = "0") int prep,
    @RequestParam(defaultValue = "0") int safetyTools,
    @RequestParam(defaultValue = "0") int newPlayers,
    @RequestParam(defaultValue = "0") int mentoring,
    @RequestParam(defaultValue = "0") int learnToPlay,
    @RequestParam(defaultValue = "0") int reviewing,
    @RequestParam(defaultValue = "0") int codeOfConduct,
    @RequestParam(defaultValue = "0") int eventOrga,
    @RequestParam(defaultValue = "0") int staffing,
    @RequestParam(defaultValue = "0") int streaming,
    @RequestParam(defaultValue = "0") int addHours,
    @RequestParam(defaultValue = "0") int addRewards,
    @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    Optional<Season> e = seasonRepository.findById(id);
    if (e.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    Season latest = e.get();
    LogEntry entry = new LogEntry();
    entry.setSeason(latest.getId());
    entry.setDate(date != null ? Date.from(date.toInstant(ZoneOffset.UTC)) : null);
    entry.setTitle(title);
    entry.setAddFullRewards(addRewards);
    entry.setAddServiceHours(addHours);
    entry.setCodeOfConduct(codeOfConduct);
    entry.setNotes(notes);
    entry.setDm(dm);
    entry.setPrepTime(prep);
    entry.setSafetyTools(safetyTools);
    entry.setNewPlayers(newPlayers);
    entry.setMentoring(mentoring);
    entry.setLearnToPlay(learnToPlay);
    entry.setReviewing(reviewing);
    entry.setEventOrga(eventOrga);
    entry.setStreaming(streaming);
    entry.setEventStaffing(staffing);
    entry.setLocation(location);
    user.getLogEntries().add(entry);
    user = userRepository.save(user);
    if (htmx) {
      model.addAttribute("offset", 0);
      model.addAttribute("edit", null);
      model.addAttribute("add", -1);
      model.addAttribute("season", latest);
      model.addAttribute("saved", true);
      model.addAttribute("locations", user.locations());
      model.addAttribute("characters", user.characters());
      model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
      model.addAttribute("assignableLogs", user.available(latest.getId()));
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id);
      response.addHeader("HX-Trigger", "logsChanged");
      model.addAttribute("user", User.builder().userId(user.getUserId()).logEntries(List.of(entry)).build());
      return "overview :: log";
    }
    else {
      return "redirect:/log/" + uid + '/' + latest.getId();
    }

  }

  @GetMapping("/delete/{uid}/{id}")
  public String deleteConfirm(@PathVariable String uid, @PathVariable String id, Model model) {
    User user = ensureLogin(getUser(uid));
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> entry = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (entry.isPresent()) {
      model.addAttribute("deleteConfirm", entry.get());
      return log(uid, entry.get().getSeason(), false, null, model);
    }
    return log(uid, model);
  }

  @GetMapping("/delete/{uid}/{id}/{reward}/{index}")
  public String deleteConfirm(@PathVariable String uid, @PathVariable String id, @PathVariable int reward, @PathVariable int index, Model model) {
    User user = ensureLogin(getUser(uid));
    Optional<Assigment> entry = user.getAssignments().stream().filter(a-> Objects.equals(id, a.getSeason()) && reward == a.getReward()).skip(index).findFirst();
    if (entry.isPresent()) {
      model.addAttribute("deleteConfirmAssignmemnt", entry.get());
      model.addAttribute("deleteConfirmIndex", index);
      return log(uid, entry.get().getSeason(), false, null, model);
    }
    return log(uid, model);
  }

  @PostMapping("/available/{uid}/{id}")
  public String getAvailable(@PathVariable String uid, @PathVariable String id, @RequestParam(required = false) String entryAsPlayer, Model model) {
    User user = getUser(uid);
    model.addAttribute("updateAvailable", true);
    if (entryAsPlayer != null && !entryAsPlayer.isEmpty()) {
      UUID uuid = UUID.fromString(entryAsPlayer);
      model.addAttribute("entryAsPlayer", uuid);
      model.addAttribute("assignableLogs", user.available(id, uuid));
    }
    else {
      model.addAttribute("assignableLogs", user.available(id));
    }
    return "overview :: available";
  }

  @PostMapping("/delete/{uid}/{id}")
  public String deleteLog(@PathVariable String uid, @PathVariable String id) {
    User user = ensureLogin(getUser(uid));
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> entry = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (entry.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    if (user.getAssignments().stream().anyMatch(a->entry.get().getId().equals(a.getEntryAsPlayer()))) {
      throw new ResponseStatusException(NOT_MODIFIED, "Log is assigned to Reward");
    }
    user.getLogEntries().removeIf(logEntry -> logEntry.getId().equals(uuid));
    userRepository.save(user);
    return "redirect:/log/" + uid + '/' + entry.get().getSeason();
  }

  @PostMapping("/delete/{uid}/{id}/{reward}/{index}")
  public String deleteAssignment(@PathVariable String uid, @PathVariable String id, @PathVariable int reward, @PathVariable int index) {
    User user = ensureLogin(getUser(uid));
    Optional<Assigment> entry = user.getAssignments().stream().filter(a-> Objects.equals(id, a.getSeason()) && reward == a.getReward()).skip(index).findFirst();
    if (entry.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    user.getAssignments().remove(entry.get());
    userRepository.save(user);
    return "redirect:/log/" + uid + '/' + entry.get().getSeason();
  }


  @DeleteMapping("/log/{uid}/{id}/{reward}/{index}")
  public String deleteAssignment(@PathVariable String uid, @PathVariable String id, @PathVariable int reward, @PathVariable int index, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    Optional<Assigment> entry = user.getAssignments().stream().filter(a-> Objects.equals(id, a.getSeason()) && reward == a.getReward()).skip(index).findFirst();
    if (entry.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    user.getAssignments().remove(entry.get());
    user = userRepository.save(user);
    Season latest = seasonRepository.findById(entry.get().getSeason()).orElse(seasonRepository.findFirstByOrderByStartDesc());
    model.addAttribute("season", latest);
    response.addHeader("HX-Push-Url", "/log/" + uid + "/" + latest.getId() + "/" + reward);
    response.addHeader("HX-Trigger", "logsChanged");
    if (entry.get().getEntryAsPlayer() != null) {
      response.addHeader("HX-Trigger-After-Settle", "UpdateLog"+entry.get().getEntryAsPlayer());
    }
    model.addAttribute("offset", reward);
    model.addAttribute("edit", null);
    model.addAttribute("add", reward);
    model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
    model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
    model.addAttribute("locations", Collections.emptyList());
    model.addAttribute("characters", user.characters());
    model.addAttribute("savedAssignment", true);
    model.addAttribute("assignableLogs", user.available(latest.getId()));
    model.addAttribute("user", user);
    RewardEntry rewardentry = new RewardEntry();
    List<RewardEntry> list = latest.getRewards();
    if (index >= 0 && reward < list.size()) rewardentry = list.get(reward);
    model.addAttribute("season", Season.builder().id(id).rewards(List.of(rewardentry)).build());
    return "overview :: reward";
  }

  @DeleteMapping("/entry/{uid}/{id}")
  public String deleteLog(@PathVariable String uid, @PathVariable String id, HttpServletResponse response, Model model) {
    User user = ensureLogin(getUser(uid));
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> entry = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (entry.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    if (user.getAssignments().stream().anyMatch(a->entry.get().getId().equals(a.getEntryAsPlayer()))) {
      throw new ResponseStatusException(NOT_MODIFIED, "Log is assigned to Reward");
    }
    user.getLogEntries().removeIf(logEntry -> logEntry.getId().equals(uuid));
    user = userRepository.save(user);
    Season latest = seasonRepository.findById(entry.get().getSeason()).orElse(seasonRepository.findFirstByOrderByStartDesc());
    model.addAttribute("season", latest);
    response.addHeader("HX-Push-Url", "/log/" + uid + "/" + latest.getId());
    response.addHeader("HX-Trigger", "logsChanged");
    model.addAttribute("user", User.builder().userId(user.getUserId()).logEntries(List.of()).build());
    model.addAttribute("offset", 0);
    model.addAttribute("edit", null);
    model.addAttribute("add", -1);
    model.addAttribute("season", latest);
    model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
    model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
    model.addAttribute("locations", Collections.emptyList());
    model.addAttribute("characters", user.characters());
    model.addAttribute("deleted", "true");
    model.addAttribute("assignableLogs", user.available(latest.getId()));
    return "overview :: deletedlog";
  }

  @GetMapping("/log/{uid}/{id}")
  public String log(@PathVariable String uid, @PathVariable String id, @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = getUser(uid);
    model.addAttribute("offset", 0);
    model.addAttribute("edit", null);
    model.addAttribute("add", -1);
    model.addAttribute("user", user);
    Optional<Season> latest = seasonRepository.findById(id);
    model.addAttribute("earned", LogEntry.getStats(id, user.getLogEntries(), user.getAssignments()));
    if (latest.isPresent()) {
      model.addAttribute("season", latest.get());
      model.addAttribute("used", Assigment.getStats(latest.get(), user.getAssignments()));
      model.addAttribute("assignableLogs", user.available(latest.get().getId()));
    }
    else {
      model.addAttribute("season", seasonRepository.findFirstByOrderByStartDesc());
      model.addAttribute("used", new Stats());
      model.addAttribute("assignableLogs", Collections.emptyList());
    }
    if (htmx) {
      latest.ifPresent(s->response.addHeader("HX-Push-Url", "/log/" + uid + "/" + s.getId()));
      return "overview :: seasontable";
    }
    else {
      model.addAttribute("seasons",seasonRepository.findAll());
      model.addAttribute("locations", user.locations());
      model.addAttribute("characters", user.characters());
      return "overview";
    }
  }
  @PatchMapping("/entry/{uid}/{id}")
  public String entry(@PathVariable String uid, @PathVariable String id, @RequestParam(defaultValue = "false") boolean triggered, HttpServletResponse response, Model model) {
    User user = getUser(uid);
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> entry = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (entry.isEmpty()) {
      throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
    }
    LogEntry logEntry = entry.get();
    model.addAttribute("offset", 0);
    model.addAttribute("edit", null);
    model.addAttribute("add", -1);
    Season latest = seasonRepository.findById(logEntry.getSeason()).orElse(seasonRepository.findFirstByOrderByStartDesc());
    model.addAttribute("season", latest);
    if (!triggered) {
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + latest.getId());
    }
    model.addAttribute("user", User.builder().userId(user.getUserId()).logEntries(List.of(logEntry)).assignments(user.getAssignments()).build());
    return "overview :: log";
  }
  @GetMapping("/entry/{uid}/{id}")
  public String entry(@PathVariable String uid, @PathVariable String id, @RequestParam(defaultValue = "false") boolean triggered, @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, @RequestParam(defaultValue="true") boolean edit, HttpServletResponse response, Model model) {
    User user = getUser(uid);
    UUID uuid = UUID.fromString(id);
    Optional<LogEntry> entry = user.getLogEntries().stream().filter(l->l.getId().equals(uuid)).findFirst();
    if (entry.isEmpty()) {
      if (htmx) {
        throw new ResponseStatusException(NOT_FOUND, "Unable to find resource");
      }
      return "redirect:/log/" + uid;
    }
    LogEntry logEntry = entry.get();
    model.addAttribute("offset", 0);
    model.addAttribute("edit", uuid);
    model.addAttribute("add", -1);
    Season latest = seasonRepository.findById(logEntry.getSeason()).orElse(seasonRepository.findFirstByOrderByStartDesc());
    model.addAttribute("season", latest);
    if (latest != null) {
      model.addAttribute("assignableLogs", user.available(latest.getId()));
    }
    else {
      model.addAttribute("assignableLogs", Collections.emptyList());
    }
    if (htmx) {
      if (!triggered) {
        response.addHeader("HX-Push-Url", "/entry/" + uid + "/" + id);
      }
      model.addAttribute("user", User.builder().userId(user.getUserId()).logEntries(List.of(logEntry)).assignments(user.getAssignments()).build());
      return "overview :: log";
    }
    else {
      if (latest != null) {
        model.addAttribute("user", user);
        model.addAttribute("earned", LogEntry.getStats(latest.getId(), user.getLogEntries(), user.getAssignments()));
        model.addAttribute("used", Assigment.getStats(latest, user.getAssignments()));
      } else {
        model.addAttribute("earned", new Stats());
        model.addAttribute("used", new Stats());
      }
      model.addAttribute("seasons",seasonRepository.findAll());
      model.addAttribute("locations", user.locations());
      model.addAttribute("characters", user.characters());
      return "overview";
    }
  }

  @GetMapping("/log/{uid}/{id}/{index}")
  public String log(@PathVariable String uid,  @PathVariable String id, @PathVariable int index, @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    User user = getUser(uid);
    model.addAttribute("edit", null);
    model.addAttribute("add", index);
    model.addAttribute("user", user);
    Optional<Season> latest = seasonRepository.findById(id);
    model.addAttribute("assignableLogs", latest.map(s->user.available(s.getId())).orElse(Collections.emptyList()));
    if (htmx) {
      model.addAttribute("offset", index);
      RewardEntry entry = new RewardEntry();
      if (latest.isPresent()) {
        List<RewardEntry> list = latest.get().getRewards();
        if (index >= 0 && index < list.size()) entry = list.get(index);
      }
      model.addAttribute("season", Season.builder().id(id).rewards(List.of(entry)).build());
      response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id + "/" + index);
      return "overview :: reward";
    }
    else {
      model.addAttribute("earned", LogEntry.getStats(id, user.getLogEntries(), user.getAssignments()));
      model.addAttribute("used", latest.map(s->Assigment.getStats(s, user.getAssignments())).orElse(new Stats()));
      latest.ifPresent(season -> model.addAttribute("season",season));
      model.addAttribute("offset", 0);
      model.addAttribute("seasons",seasonRepository.findAll());
      model.addAttribute("locations", user.locations());
      model.addAttribute("characters", user.characters());
      return "overview";
    }
  }

  @PatchMapping("/log/{uid}/{id}/{index}")
  public String log(@PathVariable String uid,  @PathVariable String id, @PathVariable int index, HttpServletResponse response, Model model) {
    User user = getUser(uid);
    model.addAttribute("edit", null);
    model.addAttribute("add", -1);
    model.addAttribute("user", user);
    Optional<Season> latest = seasonRepository.findById(id);
    model.addAttribute("offset", index);
    model.addAttribute("assignableLogs", latest.map(s->user.available(s.getId())).orElse(Collections.emptyList()));
    response.addHeader("HX-Push-Url", "/log/" + uid + "/" + id);
    RewardEntry entry = new RewardEntry();
    if (latest.isPresent()) {
      List<RewardEntry> list = latest.get().getRewards();
      if (index >= 0 && index < list.size()) entry = list.get(index);
    }
    model.addAttribute("season", Season.builder().id(id).rewards(List.of(entry)).build());
    return "overview :: reward";
  }

  @PostMapping("/newSeason")
  public String newSeason(@RequestParam String id, @RequestParam LocalDate start, @RequestParam LocalDate end, @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    testAdmin();
    Season season = new Season();
    season.setEnd(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    season.setStart(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    season.setId(id);
    season = seasonRepository.save(season);
    model.addAttribute("seasons",seasonRepository.findAll());
    model.addAttribute("levels", rewardLevelRepository.findAll());
    model.addAttribute("offset", 0);
    model.addAttribute(season);
    response.addHeader("HX-Push-Url", "/admin/" + season.getId());
    if (htmx) return "admin :: seasons";
    return "admin";
  }

  @GetMapping("/admin/{id}")
  public String adminSeason(@PathVariable String id, @RequestHeader(defaultValue = "false", name="HX-Request") boolean htmx, HttpServletResponse response, Model model) {
    testAdmin();
    Optional<Season> season = seasonRepository.findById(id);
    season.ifPresent(model::addAttribute);
    model.addAttribute("offset", 0);
    model.addAttribute("edit", false);
    model.addAttribute("levels", rewardLevelRepository.findAll());
    if (htmx) {
      season.ifPresent(s->response.addHeader("HX-Push-Url", "/admin/" + s.getId()));
      return "admin :: seasontable";
    }
    else {
      model.addAttribute("seasons",seasonRepository.findAll());
      return "admin";
    }
  }

  @DeleteMapping("/admin/{id}")
  public String deleteSeason(@PathVariable String id, HttpServletResponse response, Model model) {
    testAdmin();
    seasonRepository.deleteById(id);
    model.addAttribute("seasons",seasonRepository.findAll());
    model.addAttribute("offset",0);
    model.addAttribute("edit", false);
    Season latest = seasonRepository.findFirstByOrderByStartDesc();
    response.addHeader("HX-Push-Url", "/admin");
    if (latest != null) {
      model.addAttribute(latest);
    }
    return "admin :: seasons";
  }

  @PostMapping("/level")
  public String addLevel(@RequestParam String id, @RequestParam String name, @RequestParam(required = false, defaultValue = "0") Integer cost, @RequestParam(required = false) boolean singleSession, Model model) {
    testAdmin();
    RewardLevel level = new RewardLevel();
    level.setId(id);
    level.setName(name);
    level.setCost(Optional.ofNullable(cost).orElse(0));
    level.setSingleSession(singleSession);
    level = rewardLevelRepository.save(level);
    model.addAttribute("levels", List.of(level));
    model.addAttribute("levelIds", rewardLevelRepository.findAll());
    model.addAttribute("edit", false);
    return "admin :: line";
  }

  @DeleteMapping("/level/{id}")
  public String deleteLevel(@PathVariable String id, Model model) {
    testAdmin();
    rewardLevelRepository.deleteById(id);
    model.addAttribute("levelIds", rewardLevelRepository.findAll());
    return "admin :: rewardsswap";
  }

  @GetMapping("/level/{id}")
  public String getLevel(@PathVariable String id, @RequestParam(required = false) boolean edit, Model model) {
    testAdmin();
    rewardLevelRepository.findById(id).ifPresent(l->model.addAttribute("levels", List.of(l)));
    model.addAttribute("edit", edit);
    return "admin :: line";
  }

  @PutMapping("/level/{id}")
  public String saveLevel(@PathVariable String id, @RequestParam String name, @RequestParam(required = false) Integer cost, @RequestParam(required = false) boolean singleSession, Model model) {
    testAdmin();
    Optional<RewardLevel> level = rewardLevelRepository.findById(id);
    if (level.isPresent()) {
      level.get().setName(name);
      level.get().setCost(Optional.ofNullable(cost).orElse(0));
      level.get().setSingleSession(singleSession);
      level = level.map(rewardLevelRepository::save);
      model.addAttribute("levels", List.of(level.get()));
      model.addAttribute("levelIds", rewardLevelRepository.findAll());
      model.addAttribute("edit", false);
    }
    return "admin :: line";
  }

  @PostMapping("/admin/{id}")
  public String saveEntry(@PathVariable String id, @RequestParam String name, @RequestParam(required = false) Integer downtime, @RequestParam(required = false) Integer gp, @RequestParam(required = false) String url, @RequestParam String description, @RequestParam String level, @RequestParam(required = false) boolean allowsLevel, @RequestParam(required = false) boolean repeatable, Model model) {
    testAdmin();
    Optional<Season> season = seasonRepository.findById(id);
    if (season.isPresent()) {
      Season s = season.get();
      RewardEntry entry = new RewardEntry();
      entry.setName(name);
      entry.setDowntime(Optional.ofNullable(downtime).orElse(0));
      entry.setGp(Optional.ofNullable(gp).orElse(0));
      entry.setUrl(url);
      entry.setDescription(description);
      entry.setAllowsLevel(allowsLevel);
      entry.setRepeatable(repeatable);
      rewardLevelRepository.findById(level).ifPresent(entry::setLevel);
      s.getRewards().add(entry);
      seasonRepository.save(s);
      model.addAttribute("season", Season.builder().id(s.getId()).rewards(List.of(entry)).build());
      model.addAttribute("offset", s.getRewards().size()-1);
      return "admin :: entry";
    }
    return "empty";
  }

  @GetMapping("/admin/{id}/{index}")
  public String getReward(@PathVariable String id, @PathVariable int index, @RequestParam(required = false) boolean edit, Model model) {
    testAdmin();
    Optional<Season> season = seasonRepository.findById(id);
    if (season.isPresent()) {
      Season s = season.get();
      if (s.getRewards().size() > index && index >= 0) {
        RewardEntry entry = s.getRewards().get(index);
        model.addAttribute("season", Season.builder().id(s.getId()).rewards(List.of(entry)).build());
        model.addAttribute("edit", edit);
        model.addAttribute("offset", index);
        model.addAttribute("levels", rewardLevelRepository.findAll());
        return "admin :: entry";
      }
    }
    return "empty";
  }

  @PutMapping("/admin/{id}/{index}")
  public String updateReward(@PathVariable String id, @PathVariable int index, @RequestParam String name, @RequestParam(defaultValue = "0") int downtime, @RequestParam(defaultValue = "0") int gp, @RequestParam(required = false) String url, @RequestParam String description, @RequestParam String level, @RequestParam(required = false) boolean allowsLevel, @RequestParam(required = false) boolean repeatable, Model model) {
    testAdmin();
    Optional<Season> season = seasonRepository.findById(id);
    if (season.isPresent()) {
      Season s = season.get();
      if (s.getRewards().size() > index && index >= 0) {
        RewardEntry entry = s.getRewards().get(index);
        entry.setName(name);
        entry.setDowntime(downtime);
        entry.setGp(gp);
        entry.setUrl(url);
        entry.setDescription(description);
        entry.setAllowsLevel(allowsLevel);
        entry.setRepeatable(repeatable);
        rewardLevelRepository.findById(level).ifPresent(entry::setLevel);
        seasonRepository.save(s);
        model.addAttribute("offset", index);
        model.addAttribute("season", Season.builder().id(s.getId()).rewards(List.of(entry)).build());
        return "admin :: entry";
      }
    }
    return "empty";
  }

  private void testAdmin() {
    Principal principal = SecurityContextHolder.getContext().getAuthentication();
    if (principal == null || principal instanceof AnonymousAuthenticationToken || !userRepository.findById(principal.getName()).map(User::isAdmin).orElse(false)) {
      throw new ResponseStatusException(FORBIDDEN);
    }
  }

  @DeleteMapping("/admin/{id}/{index}")
  public String deleteReward(@PathVariable String id, @PathVariable int index, Model model) {
    testAdmin();
    Optional<Season> season = seasonRepository.findById(id);
    if (season.isPresent()) {
      Season s = season.get();
      if (s.getRewards().size() > index && index >= 0) {
        s.getRewards().remove(index);
        seasonRepository.save(s);
      }
      model.addAttribute(s);
      model.addAttribute("offset", 0);
      model.addAttribute("levels", rewardLevelRepository.findAll());
      return "admin :: seasontable";
    }
    return "empty";
  }
}
