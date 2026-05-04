package smartosc.conghung.modules.transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.modules.transfer.dto.request.CoreDebitRequest;
import smartosc.conghung.modules.transfer.service.CoreBankingService;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
@Slf4j(topic = "CORE-CONTROLLER")
public class CoreBankingController {

    private final CoreBankingService coreBankingService;

    @PostMapping("/debit")
    public ResponseEntity<Void> debit(@Valid @RequestBody CoreDebitRequest request) {

        coreBankingService.debit(request);

        return ResponseEntity.ok().build();
    }
}
