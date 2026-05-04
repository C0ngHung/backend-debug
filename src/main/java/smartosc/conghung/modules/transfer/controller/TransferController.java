package smartosc.conghung.modules.transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.common.constant.ApiConstant;
import smartosc.conghung.common.response.ApiResult;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.service.TransferService;

@RestController
@RequestMapping(ApiConstant.ApiGhostDebit.BASE)
@RequiredArgsConstructor
@Slf4j(topic = "TRANSFER-CONTROLLER")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<ApiResult<Void>> transfer(@Valid @RequestBody TransferRequest request) {

        transferService.transfer(request);

        return ResponseEntity.ok(ApiResult.success("Transfer processed", null));
    }
}
