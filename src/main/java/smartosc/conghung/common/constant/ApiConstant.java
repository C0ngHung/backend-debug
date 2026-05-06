package smartosc.conghung.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiConstant {

    public static final String BASE_API = "/api";
    public static final String VERSION_V1 = BASE_API + "/v1";

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ApiAuth {
        public static final String BASE = "/auth";
        public static final String LOGIN = "/login";
        public static final String LOGOUT = "/logout";
        public static final String REGISTER = "/register";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ApiProduct {
        public static final String BASE = VERSION_V1 + "/products";
        public static final String GET_BY_ID = "/{id}";
        public static final String GET_BY_CATEGORY = "/category/{category}";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ApiGhostDebit {
        public static final String BASE = VERSION_V1 + "/transfer";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ApiMockCore {
        public static final String BASE = "/core";
    }
}
