package de.maluku.serviceawards;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RewardLevelRepository extends MongoRepository<RewardLevel, String> {
}
