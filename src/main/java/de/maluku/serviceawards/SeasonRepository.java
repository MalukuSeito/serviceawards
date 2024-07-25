package de.maluku.serviceawards;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeasonRepository extends MongoRepository<Season, String> {
  Season findFirstByOrderByStartDesc();
}
