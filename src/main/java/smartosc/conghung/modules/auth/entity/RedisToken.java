package smartosc.conghung.modules.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("token-blacklist")
@Builder
public class RedisToken {

    @Id
    private String jwtID;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long expiredTime;
}
