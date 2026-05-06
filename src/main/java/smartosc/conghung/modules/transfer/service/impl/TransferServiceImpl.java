package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import smartosc.conghung.modules.transfer.client.CoreBankingClient;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.service.IdempotencyService;
import smartosc.conghung.modules.transfer.service.TransferService;
import smartosc.conghung.modules.transfer.vo.ExternalReference;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TRANSFER")
public class TransferServiceImpl implements TransferService {

    private final IdempotencyService idempotencyService;
    private final CoreBankingClient coreBankingClient;

    @Override
    @Transactional
    public void transfer(TransferRequest request) {

        // Bước 1: Normalize externalRef → Value Object
        // ExternalReference.from() sẽ strip() bỏ trailing space từ input
        // → Đảm bảo key nhất quán trong App layer
        // NHƯNG: khi gửi sang Core Banking, ref.value() đã bị strip rồi
        // → Nếu DB lưu CHAR(16) có padding, ref gốc ≠ ref trong DB → BUG tiềm ẩn
        ExternalReference ref = ExternalReference.from(request.getExternalRef());

        // Bước 2: "Giữ chỗ" idempotency — chạy trong REQUIRES_NEW transaction riêng
        // Nếu đã có key → return false → giao dịch trùng lặp, dừng tại đây
        // Nếu chưa có → insert record PROCESSING, return true → cho phép tiếp tục
        boolean reserved = idempotencyService.reserve(ref, request);

        if (!reserved) {

            log.info("Duplicate transfer detected, skipping");

            return;
        }

        try {
            // Bước 3: Gọi Core Banking qua REST API để thực hiện trừ tiền
            // CoreBankingClient có readTimeout = 2000ms
            // Nếu Core xử lý lâu hơn (coreDelayMillis > 2000) → ném ResourceAccessException
            // ⚠️ LƯU Ý: timeout KHÔNG có nghĩa là Core chưa xử lý
            // → Core có thể đã trừ tiền thành công nhưng response về bị timeout
            coreBankingClient.debit(
                    ref.value(),
                    request.getDebitAccountNo(),
                    request.getAmount(),
                    request.getCoreDelayMillis()
            );

            // Bước 4a: Nếu Core phản hồi thành công → cập nhật status = SUCCESS
            // Chạy trong REQUIRES_NEW transaction riêng
            idempotencyService.markSuccess(ref);

            log.info("Transfer completed successfully");

        } catch (ResourceAccessException exception) {

            // Bước 4b: Nếu timeout → KHÔNG BIẾT Core đã trừ tiền hay chưa
            // → Đánh dấu status = UNKNOWN để đội reconciliation xử lý sau
            // → Chạy trong REQUIRES_NEW transaction riêng (đảm bảo commit được)
            // → Sau đó ném lại exception để caller biết lỗi
            idempotencyService.markUnknown(ref);

            log.error("Transfer timeout — marked as UNKNOWN for reconciliation");

            throw exception;
        }
    }
}
