package smartosc.conghung.modules.auth.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtInfo implements Serializable {
    private String jwtID;
    private Date issuedAt;
    private Date expirationTime;
}
