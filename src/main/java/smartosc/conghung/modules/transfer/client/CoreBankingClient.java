package smartosc.conghung.modules.transfer.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import smartosc.conghung.modules.transfer.dto.request.CoreDebitRequest;

import java.math.BigDecimal;

@Component
@Slf4j(topic = "CORE-BANKING-CLIENT")
public class CoreBankingClient {

    private final RestClient restClient;

    public CoreBankingClient() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .requestFactory(factory)
                .build();
    }

    public void debit(String externalRef, String accountNo, BigDecimal amount, long coreDelayMillis) {

        CoreDebitRequest request = new CoreDebitRequest();
        request.setExternalRef(externalRef);
        request.setAccountNo(accountNo);
        request.setAmount(amount);
        request.setDelayMillis(coreDelayMillis);

        log.info("Sending debit request to core banking");

        restClient.post()
                .uri("/core/debit")
                .body(request)
                .retrieve()
                .toBodilessEntity();

        log.info("Core banking responded successfully");
    }
}
