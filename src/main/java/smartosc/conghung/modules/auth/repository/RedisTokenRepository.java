package smartosc.conghung.modules.auth.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import smartosc.conghung.modules.auth.entity.RedisToken;

@Repository
public interface RedisTokenRepository extends CrudRepository<RedisToken, String> {
}
